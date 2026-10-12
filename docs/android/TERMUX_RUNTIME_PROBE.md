# Termux Runtime Probe

This is the next diagnostic step before attempting to bundle Node.js inside the APK. It checks the Node version/architecture, outbound HTTPS, local HTTP streaming, and whether the installed `better-sqlite3` native addon can create/write/read a database.

**Important:** this is a diagnostic of the Node environment installed in Termux. A pass does not mean the APK contains Node.js or that the full OmniRoute server has been validated.

## Run it on the target Android phone

1. Open Termux and check that Node satisfies the repository's engine requirement: `>=22.22.2 <23 || >=24.0.0 <27`.
2. Clone the experiment branch and install dependencies using the repository's documented package manager and setup instructions.
3. From the repository root, run:

```sh
node scripts/android/termux-runtime-probe.mjs
```

4. Copy the complete JSON output. If it exits with code 1, use the first failed check to identify the next blocker.

The script does not install packages or edit repository files. The SQLite check imports the project's installed `better-sqlite3` package and removes its temporary database after testing.

## What each check means

- `node_engine`: current Node version is inside the declared supported range.
- `platform_arch`: the process reports ARM64. Termux commonly reports `linux` as the platform; this is not proof of an APK-embedded runtime.
- `outbound_https`: the runtime can reach Node.js's HTTPS website from the phone.
- `local_http_stream`: a local HTTP response can be consumed incrementally.
- `better_sqlite3`: the installed native addon loads and successfully creates, writes, and reads a SQLite database.

## After this probe

If these checks pass, run the repository's actual build/start commands and test `/health`, `/v1/models`, a real provider request, and streaming. Record failures rather than skipping native modules. If `better-sqlite3` fails, stop and solve that compatibility issue before proceeding.

Even a complete Termux pass is only the prerequisite experiment. The standalone APK still needs a reproducible Android ARM64 Node.js 24 runtime, a compatible database strategy, process lifecycle handling, and a physical-device smoke test.
