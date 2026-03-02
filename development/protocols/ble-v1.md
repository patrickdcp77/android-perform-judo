# Protocole BLE — V1 (draft)

Objectif : streaming temps réel d’un indicateur d’engagement + marqueurs de phase.

## Services / Caractéristiques (proposés)
- Service `JUDO_STREAM_SERVICE` (UUID à définir)
- `CONTROL` (Write)
  - `START_SESSION`
  - `STOP_SESSION`
  - `SET_RATE` (50/100 Hz)
- `LIVE_DATA` (Notify)
  - paquets contenant plusieurs échantillons
- `EVENT_MARK` (Write)
  - marqueurs envoyés par le smartphone (phase, label, timestamp)
- `STATUS` (Read/Notify)
  - état, batterie, compteur échantillons

## Format LIVE_DATA (suggestion)
- `uint32 t0_ms`
- `uint8 n`
- répété `n` fois :
  - `int16 a_dyn_mg`
  - `uint16 engage_inst`
  - `int16 temp_centi` (option)

## Versioning
- Première octet optionnel : `protocol_version` (ex: 1)
- Garder le protocole rétrocompatible.

