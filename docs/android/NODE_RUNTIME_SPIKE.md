# Android Node.js Runtime Spike

Status: investigation completed; runtime integration is blocked pending a compatible runtime build.

## Why this spike matters

OmniRoute's package declares Node.js `>=22.22.2 <23 || >=24.0.0 <27`. A runtime that only embeds Node.js 18 cannot be treated as compatible, even if it can run a basic HTTP server.

## Candidate reviewed

### nodejs-mobile/nodejs-mobile

- Project: https://github.com/nodejs-mobile/nodejs-mobile
- Android getting-started guide: https://nodejs-mobile.github.io/docs/guide/guide-android/getting-started/
- Releases: https://github.com/nodejs-mobile/nodejs-mobile/releases

The official releases page surfaced Node.js for Mobile Apps core library v18.20.4 and Android ABI builds (including `arm64-v8a`). The documented integration uses a native shared library and a JNI/C++ bridge to invoke Node in-process. This is a real embedded Node runtime, but the reviewed release is Node 18, below OmniRoute's declared supported versions.

**Decision: do not integrate this Node 18 runtime into OmniRoute.** Doing so would create a misleading proof of compatibility and likely fail on newer Node APIs/dependencies. Do not loosen OmniRoute's engine requirement just to make the Android demo start.

## Next acceptable path

1. Find or build a reproducible Android `arm64-v8a` Node.js 24 runtime, with a documented upstream source, pinned version, license, checksum, and build process.
2. Verify the runtime can load ordinary JS packages and make HTTPS requests on the target Android version.
3. Prove the required module format and native-addon ABI for the exact dependencies we intend to ship.
4. Validate SQLite separately. `better-sqlite3` is a native addon and cannot be assumed to work with a mobile Node build; evaluate a supported mobile build or an existing pure-JS/WASM SQLite option.
5. Only after the above, replace the Kotlin-only health server with a runtime adapter that starts Node and checks readiness.
6. Keep full Next.js/gateway integration blocked until the runtime passes those checks.

## Known blockers in the existing app

- The current Kotlin `OmniRouteService` serves only `/health`; it does not start Node.js.
- The native Node integration documented by nodejs-mobile requires a JNI/C++ bridge and a matching Android `libnode.so`; merely adding a Maven dependency or copying a desktop Node binary is not sufficient.
- OmniRoute's package engine requirement, native SQLite, Next.js production output, and other native modules must all be evaluated against the same runtime.
- The physical Android ARM64 smoke test has not yet been run.

## Acceptance gate

Do not claim a working OmniRoute APK until a debug APK has been installed on a physical ARM64 Android device and all of these pass:

- runtime reports a supported Node version;
- loopback health endpoint responds;
- HTTPS outbound request succeeds;
- streaming response works;
- persistent database create/read/write succeeds;
- runtime stops and restarts without creating duplicate listeners.

This document records an engineering blocker, not a completed runtime integration.
