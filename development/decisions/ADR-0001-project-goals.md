# ADR-0001 — Objectif et périmètre V1

## Statut
Accepté

## Contexte
On veut mesurer et afficher en temps réel un indicateur d’engagement pendant une séance de judo, avec découpage en phases (tops) et export.

## Décision
V1 vise :
- 1 capteur (ESP32‑C3 + MPU‑6050) en BLE
- streaming d’un score simple (et/ou a_dyn) à 50 Hz (batché)
- UI Android Compose : graphe live + boutons tops + chrono
- export CSV en fin de séance

## Conséquences
- Le calcul “engagement” est idéalement côté capteur pour réduire le débit BLE.
- L’app doit être robuste aux déconnexions/reconnexions.

