# Android full-app compatibility experiment

This branch is an incremental feasibility effort to run OmniRoute locally inside an Android APK, with no separately hosted OmniRoute backend. It is not yet a working APK.

## Documents

- [End-to-end implementation plan](./IMPLEMENTATION_PLAN.md)
- [Android compatibility matrix](./COMPATIBILITY_MATRIX.md)

## Current assessment

- OmniRoute 3.8.52 declares a Node.js runtime range of `>=22.22.2 <23 || >=24.0.0 <27`.
- The project uses a server-rendered Next.js application and Node backend. A WebView-only APK would not run that backend.
- The desktop package uses Electron, which does not provide an Android APK target in this repository.
- Native/deployment-sensitive components include SQLite and optional proxy/browser tooling. These must be individually audited and tested against Android/ARM64.

## Work completed in this branch

- Added a static runtime compatibility audit workflow.
- Added the full phased implementation plan and a provisional compatibility matrix.

## Next implementation milestone

Build a minimal Android APK that bundles a validated Android-compatible Node runtime, starts a health endpoint bound to `127.0.0.1`, and proves SQLite persistence on a physical Android ARM64 device. Only after that proof should the existing OmniRoute server be integrated.

## Important limitation

The current workflow is a static audit, not an Android build. No APK is being advertised as ready, and no on-device runtime test has yet been recorded.
