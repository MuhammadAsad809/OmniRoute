# Android Compatibility Matrix

Status is based on repository inspection and remains provisional until verified by an Android build and device tests.

| Area | Existing implementation | Android assessment | Next action |
|---|---|---|---|
| Main runtime | Node.js + Next.js App Router | Not yet proven in a bundled Android runtime | Phase 1 runtime proof-of-concept, then start the production server |
| Node version | package.json declares >=22.22.2 <23 or >=24.0.0 <27 | Runtime packaging must match supported Node APIs and Android ABI | Select a maintained Android-compatible runtime and verify Node version |
| Electron | Desktop shell spawns the server as a child process | Electron desktop package is not an Android APK target | Do not reuse Electron shell; implement native Android shell/service |
| Provider adapters | Existing OmniRoute adapters and API translation | Mostly application-level code, but each adapter may assume runtime APIs or browser behavior | Preserve and test adapter-by-adapter |
| Unified /v1 API | Existing gateway API routes | Target feature; depends on successful local server startup | Test models, chat, streaming, authentication, errors |
| SQLite | better-sqlite3/native SQLite dependency in server stack | Native binary compatibility is a high-risk blocker | Test Android ABI support or replace with a compatible SQLite strategy behind an adapter |
| Workspace packages | npm workspaces including open-sse and browser-pool | Workspace dependencies must be staged into production runtime | Build a minimal production dependency bundle and test resolution paths |
| Browser automation | Playwright/browser-pool | Desktop browser binaries and process assumptions are unlikely to transfer directly | Mark optional until a separate Android feasibility spike passes |
| Filesystem | Existing server expects Node filesystem and app/project paths | Android package assets are not a writable working directory | Use app-private data paths and an explicit runtime root |
| Process management | Server/desktop scripts may spawn child processes | Android child-process and lifecycle assumptions need validation | Keep process ownership in Foreground Service; test termination and cleanup |
| Streaming | HTTP/SSE support in the gateway | Must be verified through Android runtime and local client | Test SSE, long responses, cancellation, and socket cleanup |
| OAuth | Provider-specific browser redirects/callbacks | Compatibility varies by provider and redirect scheme | Test each OAuth provider separately; do not promise blanket support |
| MCP/A2A | Existing transport and tool integrations | Transport, sockets, process and dependency assumptions may differ | Inventory each transport and test separately |
| MITM/DNS/certificates | Optional proxy/system-network features | Android OS permissions and unrooted-device restrictions may prevent parity | Keep disabled/unsupported until a dedicated design and security review |
| Secrets | Provider credentials and local configuration | Must remain app-private and be redacted from logs | Add encrypted credential storage and diagnostic redaction |
| Background runtime | No native Android lifecycle in the desktop app | Requires a native foreground service and persistent notification | Test on target device across screen-off, battery saver, and low memory |

## Known high-risk blockers

1. A supported Node.js runtime must be packaged and launched on Android ARM64.
2. The production server's dependency tree must work without unsupported native binaries.
3. better-sqlite3 must either work with an Android-compatible binary/build process or be replaced behind a storage abstraction.
4. Next.js production output and runtime asset paths must resolve inside app-private storage.
5. Foreground-service behavior must be tested on the actual target Android version and device manufacturer.

## Status labels

- **Not started:** no implementation or verification yet.
- **In progress:** code exists but has not passed device verification.
- **Verified:** test evidence exists for a stated Android version, ABI, and test case.

No item should be labelled Verified until the exact test is run and recorded.