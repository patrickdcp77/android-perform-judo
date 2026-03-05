#include <Arduino.h>
#include <Wire.h>

#include <NimBLEDevice.h>

#include "I2Cdev.h"
#include "MPU6050.h"

// -------------------------
// BLE UUIDs (doivent matcher l'app Android BleUuids.kt)
// -------------------------
static NimBLEUUID JUDO_STREAM_SERVICE("0000fe40-cc7a-482a-984a-7f2ed5b3e58f");
static NimBLEUUID CONTROL_CHAR_UUID("0000fe41-cc7a-482a-984a-7f2ed5b3e58f");
static NimBLEUUID LIVE_DATA_CHAR_UUID("0000fe42-cc7a-482a-984a-7f2ed5b3e58f");
static NimBLEUUID EVENT_MARK_CHAR_UUID("0000fe43-cc7a-482a-984a-7f2ed5b3e58f");
static NimBLEUUID STATUS_CHAR_UUID("0000fe44-cc7a-482a-984a-7f2ed5b3e58f");

// -------------------------
// Streaming config
// -------------------------
// On produit 50 Hz (1 sample toutes les 20 ms)
static constexpr uint32_t SAMPLE_PERIOD_MS = 20;
// On batch 10 samples / notification => 5 notifs/s
static constexpr uint8_t SAMPLES_PER_PACKET = 10;

// MPU-6050 ranges
// +/- 4g pour un bon compromis (8192 LSB/g)
static constexpr uint8_t ACCEL_RANGE = MPU6050_ACCEL_FS_4;

// Pour convertir en mg selon la plage
static float accelLsbPerG(uint8_t range) {
    switch (range) {
        case MPU6050_ACCEL_FS_2:  return 16384.0f;
        case MPU6050_ACCEL_FS_4:  return 8192.0f;
        case MPU6050_ACCEL_FS_8:  return 4096.0f;
        case MPU6050_ACCEL_FS_16: return 2048.0f;
        default: return 8192.0f;
    }
}

// -------------------------
// Simple engagement estimator (V1)
// -------------------------
// a_dyn_mg = | |a| - 1g | (approx), en mg
// engage_inst = moyenne glissante (fenêtre 1s) de a_dyn_mg, clampée en 0..65535
static constexpr uint16_t ENGAGE_SCALE = 1; // 1 unité = 1 mg
static constexpr uint16_t ENGAGE_MAX = 65535;

// Fenêtre 1 sec -> 50 samples
static constexpr uint16_t WINDOW_SAMPLES = 50;
static uint32_t windowSum = 0;
static uint16_t windowBuf[WINDOW_SAMPLES];
static uint16_t windowIdx = 0;
static bool windowFilled = false;

static inline uint16_t updateWindow(uint16_t aDynMg) {
    if (!windowFilled) {
        windowBuf[windowIdx++] = aDynMg;
        windowSum += aDynMg;
        if (windowIdx >= WINDOW_SAMPLES) {
            windowIdx = 0;
            windowFilled = true;
        }
        uint16_t count = windowFilled ? WINDOW_SAMPLES : windowIdx;
        if (count == 0) return 0;
        return (uint16_t)min<uint32_t>(ENGAGE_MAX, (windowSum / count) * ENGAGE_SCALE);
    }

    // fenêtre circulaire
    windowSum -= windowBuf[windowIdx];
    windowBuf[windowIdx] = aDynMg;
    windowSum += aDynMg;
    windowIdx = (uint16_t)((windowIdx + 1) % WINDOW_SAMPLES);

    return (uint16_t)min<uint32_t>(ENGAGE_MAX, (windowSum / WINDOW_SAMPLES) * ENGAGE_SCALE);
}

// -------------------------
// Globals
// -------------------------
static MPU6050 mpu;

static NimBLEServer* server = nullptr;
static NimBLECharacteristic* liveDataChar = nullptr;
static NimBLECharacteristic* controlChar = nullptr;

static bool deviceConnected = false;
static bool streaming = false;
static uint32_t sessionStartMs = 0;

// Packet buffer
// Format little-endian:
// uint32 t0_ms
// uint8 n
// n * (int16 a_dyn_mg, uint16 engage_inst)
static uint8_t packet[4 + 1 + SAMPLES_PER_PACKET * 4];
static uint8_t packetCount = 0;
static uint32_t packetT0 = 0;

static void writeU32LE(uint8_t* dst, uint32_t v) {
    dst[0] = (uint8_t)(v & 0xFF);
    dst[1] = (uint8_t)((v >> 8) & 0xFF);
    dst[2] = (uint8_t)((v >> 16) & 0xFF);
    dst[3] = (uint8_t)((v >> 24) & 0xFF);
}

static void writeI16LE(uint8_t* dst, int16_t v) {
    dst[0] = (uint8_t)(v & 0xFF);
    dst[1] = (uint8_t)((v >> 8) & 0xFF);
}

static void writeU16LE(uint8_t* dst, uint16_t v) {
    dst[0] = (uint8_t)(v & 0xFF);
    dst[1] = (uint8_t)((v >> 8) & 0xFF);
}

class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* /*pServer*/, NimBLEConnInfo& /*connInfo*/) override {
        deviceConnected = true;
        Serial.println("BLE: connected");
    }

    void onDisconnect(NimBLEServer* pServer, NimBLEConnInfo& /*connInfo*/, int /*reason*/) override {
        deviceConnected = false;
        streaming = false;
        Serial.println("BLE: disconnected");
        // Re-advertise
        NimBLEDevice::startAdvertising();
    }
};

class ControlCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& /*connInfo*/) override {
        std::string v = c->getValue();
        if (v.size() == 0) return;

        // Protocole simple pour v1:
        // 0x01 = START_SESSION
        // 0x02 = STOP_SESSION
        uint8_t cmd = (uint8_t)v[0];
        if (cmd == 0x01) {
            streaming = true;
            sessionStartMs = millis();
            packetCount = 0;
            windowSum = 0;
            windowIdx = 0;
            windowFilled = false;
            Serial.println("CONTROL: START_SESSION");
        } else if (cmd == 0x02) {
            streaming = false;
            packetCount = 0;
            Serial.println("CONTROL: STOP_SESSION");
        } else {
            Serial.print("CONTROL: unknown cmd=");
            Serial.println(cmd);
        }
    }
};

static void setupBle() {
    NimBLEDevice::init("JudoSensor-MPU");
    NimBLEDevice::setPower(ESP_PWR_LVL_P9); // puissance max (utile dojo)

    server = NimBLEDevice::createServer();
    server->setCallbacks(new ServerCallbacks());

    NimBLEService* svc = server->createService(JUDO_STREAM_SERVICE);

    controlChar = svc->createCharacteristic(
        CONTROL_CHAR_UUID,
        NIMBLE_PROPERTY::WRITE
    );
    controlChar->setCallbacks(new ControlCallbacks());

    liveDataChar = svc->createCharacteristic(
        LIVE_DATA_CHAR_UUID,
        NIMBLE_PROPERTY::NOTIFY
    );

    // (optionnels pour plus tard)
    svc->createCharacteristic(EVENT_MARK_CHAR_UUID, NIMBLE_PROPERTY::WRITE);
    svc->createCharacteristic(STATUS_CHAR_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);

    svc->start();

    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    adv->setAppearance(0x0000);
    adv->addServiceUUID(JUDO_STREAM_SERVICE);
    adv->setScanResponse(true);
    adv->start();

    Serial.println("BLE: advertising started");
}

static bool setupMpu() {
    Wire.begin();

    mpu.initialize();

    if (!mpu.testConnection()) {
        Serial.println("MPU6050: connection failed");
        return false;
    }

    mpu.setFullScaleAccelRange(ACCEL_RANGE);

    // Filtre passe-bas interne (optionnel). 42Hz est un bon départ.
    mpu.setDLPFMode(MPU6050_DLPF_BW_42);

    Serial.println("MPU6050: OK");
    return true;
}

static uint16_t computeADynMg(int16_t ax, int16_t ay, int16_t az) {
    // |a| en g (approx)
    const float lsbPerG = accelLsbPerG(ACCEL_RANGE);
    const float xg = (float)ax / lsbPerG;
    const float yg = (float)ay / lsbPerG;
    const float zg = (float)az / lsbPerG;

    const float mag = sqrtf(xg * xg + yg * yg + zg * zg);
    const float dyn = fabsf(mag - 1.0f);

    uint32_t mg = (uint32_t)lroundf(dyn * 1000.0f);
    if (mg > 20000) mg = 20000; // clamp sécurité
    return (uint16_t)mg;
}

static void flushPacketIfReady() {
    if (!deviceConnected) return;
    if (!streaming) return;
    if (!liveDataChar) return;
    if (packetCount < SAMPLES_PER_PACKET) return;

    // Header
    writeU32LE(&packet[0], packetT0);
    packet[4] = packetCount;

    const size_t len = 4 + 1 + (packetCount * 4);
    liveDataChar->setValue(packet, len);
    liveDataChar->notify();

    packetCount = 0;
}

static void appendSampleToPacket(uint32_t tMs, int16_t aDynMgSigned, uint16_t engageInst) {
    if (packetCount == 0) {
        packetT0 = tMs;
    }

    // Offset après header
    const size_t base = 5 + (packetCount * 4);
    writeI16LE(&packet[base + 0], aDynMgSigned);
    writeU16LE(&packet[base + 2], engageInst);

    packetCount++;
}

void setup() {
    Serial.begin(115200);
    delay(300);

    Serial.println("Boot: esp32c3-mpu6050-ble");

    if (!setupMpu()) {
        Serial.println("ERROR: MPU init failed");
    }

    setupBle();

    // Démarrage auto du streaming en v1 (pratique pour tester rapidement)
    streaming = true;
    sessionStartMs = millis();
}

void loop() {
    static uint32_t lastSampleAt = 0;

    const uint32_t now = millis();
    if ((uint32_t)(now - lastSampleAt) < SAMPLE_PERIOD_MS) {
        // laisse du temps à NimBLE
        delay(1);
        return;
    }
    lastSampleAt = now;

    if (!streaming) {
        delay(5);
        return;
    }

    int16_t ax, ay, az;
    mpu.getAcceleration(&ax, &ay, &az);

    const uint16_t aDynMg = computeADynMg(ax, ay, az);
    const uint16_t engage = updateWindow(aDynMg);

    const uint32_t tRel = now - sessionStartMs;

    // a_dyn_mg doit être int16 dans le protocole.
    const int16_t aDynMgSigned = (aDynMg > 32767) ? 32767 : (int16_t)aDynMg;

    appendSampleToPacket(tRel, aDynMgSigned, engage);
    flushPacketIfReady();
}

