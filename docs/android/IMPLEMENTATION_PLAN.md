# OmniRoute Android APK — Implementation Plan

## Goal

Run OmniRoute's existing gateway on the user's Android device, similar to a local runtime bundled with an app. The app must not depend on a separately hosted OmniRoute backend, VPS, or cloud database. AI provider APIs remain external and require internet access.

This document is a plan, not a claim that an APK already builds or works.

## Target architecture

- Android application shell and mobile controls.
- Android foreground service responsible for runtime lifecycle and persistent notification.
- A compatible Node.js runtime bundled with the APK or installed into app-private storage.
- Existing OmniRoute backend/API and dashboard running on-device.
- Local SQLite/configuration/usage storage.
- Loopback-only API by default (127.0.0.1); optional LAN access must be separately authenticated and explicitly enabled.

## Non-goals and constraints

- Do not replace OmniRoute with a WebView-only shell; that would not run the gateway.
- Do not add a hosted relay/backend.
- Do not promise all desktop features will work on Android without testing.
- Provider inference still runs on each provider's servers.
- Android may terminate processes under force-stop, memory pressure, reboot, or manufacturer battery policies. A foreground service improves persistence but cannot guarantee immortality.
- Never commit provider secrets, signing keys, or generated user databases.

## Phase 0 — Baseline and dependency inventory

1. Pin the source baseline to release/v3.8.52 and work on android/full-apk-experiment.
2. Inventory package scripts, workspaces, native dependencies, process spawning, filesystem assumptions, environment variables, SQLite bindings, browser automation, proxy/certificate features, and server entry points.
3. Classify each feature: compatible, needs replacement, optional/unsupported, or requires a separate technical spike.
4. Keep a record of tested device ABI, Android version, Node runtime version, and build tool versions.

**Exit criteria:** a reviewed compatibility matrix; no unsupported feature is silently claimed as working.

## Phase 1 — Android runtime proof of concept

1. Select and validate a maintained Android-compatible Node runtime strategy for ARM64.
2. Build a minimal Android test app that starts a bundled Node process from app-private storage.
3. Start a local HTTP health endpoint bound to 127.0.0.1.
4. Test request/response, graceful shutdown, process exit detection, stdout/stderr capture, and runtime restart.
5. Validate HTTPS outbound requests and server-sent events/streaming.
6. Validate SQLite create/read/write/persistence using the exact library strategy intended for OmniRoute.

**Exit criteria:** installed debug APK starts the local runtime and passes a health request on a physical Android ARM64 device. Do not proceed to full integration if this fails.

## Phase 2 — Integrate the existing OmniRoute server

1. Build the existing Next.js/backend output for local execution, preserving API routes and routing logic.
2. Stage required runtime files and production dependencies into the app package/private runtime directory.
3. Replace desktop-only process, path, or platform assumptions behind a small platform adapter where possible.
4. Audit native modules individually; do not assume desktop Linux binaries work on Android.
5. Start the server with explicit app-private data paths and a loopback bind address.
6. Expose a health/readiness check and capture startup failures in a redacted diagnostic log.
7. Load the existing dashboard from the local server only after readiness succeeds.

**Exit criteria:** dashboard loads and a real authenticated /v1 request reaches the existing routing engine locally.

## Phase 3 — Foreground service and lifecycle

1. Implement a Kotlin foreground service with a persistent OmniRoute Running notification and Stop action.
2. Ensure a single runtime instance using service ownership and a lock/health check.
3. Start the runtime only after Android has allowed foreground-service startup and notification setup.
4. Reconnect the UI to an existing service instead of spawning duplicate processes.
5. Handle process crash, startup timeout, failed health check, graceful stop, and service destruction.
6. Test screen-off, app switching, low-memory conditions, battery saver, and manufacturer-specific battery management.
7. Add optional boot recovery only after reviewing Android version and background-start restrictions; never assume boot auto-start is universally allowed.

**Exit criteria:** app minimize/screen-off tests pass on the target device; notification accurately reflects runtime state.

## Phase 4 — Local provider gateway

1. Preserve existing provider adapters and model mapping wherever compatible.
2. Support adding, editing, testing, and removing provider credentials.
3. Generate/manage the unified local API credential using the existing gateway logic where possible.
4. Validate /v1/models, chat completions, streaming, timeouts, errors, retries, and fallback.
5. Keep provider credentials in app-private storage and use Android Keystore-backed encryption where practical.
6. Redact credentials from logs, error messages, crash reports, and exported diagnostics.
7. Make clear that provider-side rate limits, API charges, and terms still apply.

**Exit criteria:** at least one API-key provider passes a real request end-to-end, followed by a provider compatibility matrix for additional adapters.

## Phase 5 — Feature parity by category

- P0: runtime lifecycle, local dashboard, provider API keys, unified API auth, model listing, chat request, SQLite persistence.
- P1: streaming, model/account fallback, retries, usage and cost tracking, settings export/import.
- P2: OAuth flows, embeddings, image/audio/video endpoints, web search, moderation, reranking, MCP/A2A, memory and skills.
- P3: browser pool/Playwright, MITM proxy, DNS and certificate management, desktop-only integrations.

Every feature must be individually tested. Browser automation, system certificate management, VPN/proxy behavior, and native binaries may require redesign or may be unavailable on unrooted Android.

## Phase 6 — Security and networking

- Bind to 127.0.0.1 by default.
- Keep LAN access off by default; if implemented, require explicit opt-in, authentication, and clear security warnings.
- Do not expose the gateway publicly or add a tunnel/relay by default.
- Use Android app-private storage; do not request broad file access unless strictly needed.
- Validate local API authentication, CORS behavior, request size limits, and error handling.
- Provide a redacted diagnostic export and a clear delete-local-data action.

## Phase 7 — Build, test, release

1. Add a dedicated Android Gradle project without disturbing desktop build scripts.
2. Add GitHub Actions to build a debug APK after the runtime proof of concept is real.
3. Run static checks and tests for the native shell and runtime integration.
4. Install the APK on a physical ARM64 device and verify the complete critical path.
5. Publish APK artifacts only when the build and smoke tests pass.
6. Add release signing through GitHub secrets only when needed; never commit keystore files.
7. Document install, permissions, battery settings, known limitations, upgrade, backup, and uninstall behavior.

## Required release acceptance tests

- APK installs on the target Android version/ABI.
- Runtime starts, health check passes, and the service notification is visible.
- Minimize/screen-off behavior is tested and documented.
- Reopening the UI does not create a second server.
- Authenticated local /v1/models request works.
- One real provider chat request and streaming request work.
- Runtime stops cleanly and restarts without corrupting local data.
- API keys do not appear in logs or diagnostics.
- Provider settings and usage persist across app restarts.
- APK build and smoke-test results are linked to the release.

## Immediate next task

Complete the compatibility inventory and implement Phase 1 only: a small Android APK that bundles a validated Node runtime, starts a loopback health endpoint, and proves SQLite persistence on a physical Android ARM64 device. Integrate the full OmniRoute server only after this test passes.
