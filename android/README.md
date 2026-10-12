# OmniRoute Android — local API bridge preview

This APK includes a native, loopback-only OpenAI-compatible API bridge. It is a useful first local gateway slice, but it is not the full Node.js/Next.js OmniRoute runtime.

## What exists now

- Native Android app shell with Home, Providers, API, and Settings screens.
- Provider credential add/remove UI with AES-GCM encryption backed by Android Keystore.
- Local API settings screen with encrypted token generation and explicit gateway readiness diagnostics.
- Delete-all-local-credentials control.
- Start/Stop controls for the foreground service.
- Foreground service with a persistent notification.
- Loopback-only HTTP API at `127.0.0.1:20128` with `/health`, authenticated `GET /v1/models`, and authenticated `POST /v1/chat/completions` routes.
- OpenAI-compatible provider configuration: provider name, HTTPS base URL, exact upstream model ID, and encrypted API key.
- Provider requests are forwarded directly to the configured OpenAI-compatible endpoint; provider API keys are never sent back to API clients.
- Local API Bearer token is shown once at generation time and can be copied to the clipboard.
- GitHub Actions workflow assembles a debug APK and uploads it as an artifact after a successful build.
- An instrumentation smoke-test source is maintained for the loopback health endpoint; the current CI workflow does not run an emulator test.

The health response deliberately includes `"gatewayIntegrated":false` and `"openAiCompatibleBridge":true`. The native bridge supports only `/v1/models` and non-streaming `/v1/chat/completions` for configured OpenAI-compatible providers. It does not bundle or launch Node.js, Next.js, SQLite, or OmniRoute's full adapter/routing engine. Streaming, OAuth, fallback, usage analytics, and the broader OmniRoute API are not implemented in this bridge.

## Build

From the repository root, with JDK 17, Android SDK, and Gradle 8.9 installed:

```bash
gradle --no-daemon -p android assembleDebug
```

Expected output, if the build succeeds:

`android/app/build/outputs/apk/debug/app-debug.apk`

Or run the [Android APK build workflow](https://github.com/MuhammadAsad809/OmniRoute/actions/workflows/android-apk.yml) and download its `omniroute-android-debug` artifact after a successful run.

## Smoke test on a device

1. Install the debug APK on an Android device.
2. Open OmniRoute, generate a local API token on the API screen, and copy it.
3. Add a provider with an HTTPS OpenAI-compatible base URL, exact model ID, and API key.
4. Start the local service.
5. Request `http://127.0.0.1:20128/health`, then use `GET /v1/models` with `Authorization: Bearer YOUR_LOCAL_TOKEN`.
6. Send a non-streaming OpenAI-compatible `POST /v1/chat/completions` request using the returned model ID.
7. Minimize the app and retest; record device model, Android version, and result.
8. Tap **Stop service** and confirm the endpoint no longer responds.

## Runtime compatibility investigation

See [Node.js Runtime Spike](../docs/android/NODE_RUNTIME_SPIKE.md). The reviewed official nodejs-mobile release is Node 18, while OmniRoute requires Node.js 22.22.2 or 24–26. It must not be integrated as if it were compatible.

## Next step

The latest CI run confirms that the native bridge compiles and the debug APK artifact was uploaded. That is not proof of a successful request on a physical phone. Next, verify the bridge on a physical ARM64 device, then prove a reproducible Android ARM64 Node.js 24 runtime and a compatible persistent SQLite strategy. The goal remains to embed the existing OmniRoute Node.js/Next.js server rather than treating the bridge as a replacement for it.
