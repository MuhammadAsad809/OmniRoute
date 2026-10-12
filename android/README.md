# OmniRoute Android scaffold

This is an early lifecycle proof-of-concept, not the complete OmniRoute gateway.

## What exists now

- Native Android app shell with Start/Stop controls.
- Foreground service with a persistent notification.
- Loopback-only HTTP health endpoint at `127.0.0.1:18765/health`.
- GitHub Actions workflow to assemble a debug APK and upload it as an artifact.

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

## Next step

Replace the temporary Kotlin health server with a validated Android-compatible Node runtime, then integrate OmniRoute's real server and SQLite strategy. Do not treat this scaffold as a functional AI gateway.
