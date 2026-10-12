# OmniRoute Android scaffold

This is an early lifecycle proof-of-concept, not the complete OmniRoute gateway.

## What exists now

- Native Android app shell with Home, Providers, API, and Settings screens.
- Provider credential add/remove UI with AES-GCM encryption backed by Android Keystore.
- Local API settings screen with encrypted token generation and explicit gateway readiness diagnostics.
- Delete-all-local-credentials control.
- Start/Stop controls for the temporary lifecycle test service.
- Foreground service with a persistent notification.
- Loopback-only HTTP health endpoint at `127.0.0.1:18765/health`.
- GitHub Actions workflow to assemble a debug APK, run an Android API 35 emulator smoke test, and upload the APK artifact only if the job succeeds.
- Instrumentation smoke test that launches the app, starts the foreground service, requests the loopback health endpoint, and checks that the scaffold does not claim gateway integration.

The health response deliberately includes `"gatewayIntegrated":false`. This scaffold does not yet bundle or launch Node.js, Next.js, SQLite, provider adapters, or the actual OmniRoute `/v1` routes.

## Build

From the repository root, with JDK 17, Android SDK, and Gradle 8.9 installed:

```bash
gradle --no-daemon -p android assembleDebug
```

Expected output, if the build succeeds:

`android/app/build/outputs/apk/debug/app-debug.apk`

Or run the **Android APK scaffold** workflow from GitHub Actions and download its `omniroute-android-debug` artifact after a successful run.

## Smoke test on a device

1. Install the debug APK on an Android device.
2. Open OmniRoute and tap **Start local service**.
3. From a local terminal/ADB shell, request `http://127.0.0.1:18765/health`.
4. Confirm the response reports `runtime=android-scaffold` and `gatewayIntegrated=false`.
5. Minimize the app and repeat the request; record device model, Android version, and result.
6. Tap **Stop local service** and confirm the endpoint no longer responds.

## Runtime compatibility investigation

See [Node.js Runtime Spike](../docs/android/NODE_RUNTIME_SPIKE.md). The reviewed official nodejs-mobile release is Node 18, while OmniRoute requires Node.js 22.22.2 or 24–26. It must not be integrated as if it were compatible.

## Next step

Find or build a reproducible Android ARM64 Node.js 24 runtime, validate outbound HTTPS and streaming, and prove a compatible persistent SQLite strategy. Only then replace the temporary Kotlin health server with a real Node runtime adapter. Do not treat this scaffold as a functional AI gateway.
