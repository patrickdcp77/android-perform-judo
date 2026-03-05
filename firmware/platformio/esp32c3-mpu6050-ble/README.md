# esp32c3-mpu6050-ble (PlatformIO)

Firmware de test pour ESP32‑C3 (Arduino) : lit un MPU‑6050 en I2C et stream des paquets BLE `LIVE_DATA` (Notify) compatibles avec l'app Android.

## Protocole LIVE_DATA v1 (MPU only)

Payload binaire little‑endian :

- `uint32 t0_ms`
- `uint8 n`
- répété `n` fois:
  - `int16 a_dyn_mg`
  - `uint16 engage_inst`

UUIDs (placeholders) : voir `src/main.cpp` et `app/.../BleUuids.kt` côté Android — ils doivent matcher.

## Build / Upload (PlatformIO)

Ouvre ce dossier comme projet PlatformIO.

- Environment: `esp32c3_supermini`
- Board: `esp32-c3-devkitm-1` (à ajuster si besoin)

## Câblage (général)

MPU‑6050 en I2C:
- VCC -> 3.3V
- GND -> GND
- SDA -> SDA (GPIO selon ta carte)
- SCL -> SCL (GPIO selon ta carte)

> Note: les broches SDA/SCL exactes dépendent de la carte SuperMini. Si tu me donnes le pinout (ou une photo nette), je te mets les bonnes valeurs et j'ajoute `Wire.begin(SDA, SCL)`.

