# Android full-app compatibility experiment

This is an experimental feasibility check, not a claim that OmniRoute already runs on Android.

## Current assessment

- OmniRoute 3.8.52 declares a Node.js runtime range of `>=22.22.2 <23 || >=24.0.0 <27`.
- The project has a server-rendered Next.js application and CLI/backend entry points. A WebView-only APK would not run that backend.
- The desktop package uses Electron, which does not provide an Android APK target in this repository.
- The repository includes native/deployment-sensitive components (including SQLite and optional proxy/native tooling). Each must be audited and tested against Android/ARM64 before a self-contained APK can be claimed.

## Proposed implementation stages

1. Run a dependency/runtime compatibility audit.
2. Choose an Android-compatible Node runtime and prove that the backend starts on-device.
3. Package the backend, persistent storage, lifecycle handling, and dashboard in an Android app.
4. Build and test a debug APK on Android ARM64; publish it only after the backend health check succeeds.

## Important limitation

The first workflow only performs a static compatibility audit. It deliberately does not upload a placeholder APK or claim the app is working. A real full APK requires a compatible on-device Node runtime and successful backend integration tests.
