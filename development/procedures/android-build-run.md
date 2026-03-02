# Procédure — Build & Run Android (local)

## Pré-requis
- Android Studio installé
- JDK configuré par Android Studio (Toolchains)

## Étapes (Android Studio)
1. Ouvrir le projet `engagementjudo/`.
2. Attendre la synchro Gradle.
3. Choisir une target (émulateur ou téléphone).
4. Lancer **Run** sur l’app `app`.

## Étapes (CLI)
> À utiliser si tu veux lancer via terminal.

```powershell
cd "C:\Users\patri\AndroidStudioProjects\engagementjudo"
./gradlew.bat :app:assembleDebug
```

Résultat attendu : un APK debug généré dans `app/build/outputs/apk/`.

