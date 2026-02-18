# LanShare Desktop

LanShare Desktop is a LAN-only desktop application for macOS, Windows, and Linux.
It provides:

- automatic host discovery on LAN
- host locale pubblicato con IP LAN auto-rilevato (non localhost)
- optional auto-discovery refresh every 5s
- quick LAN join senza PIN, con TLS + fingerprint TOFU
- drag-and-drop file and folder sharing with resume
- manual file/folder picker, queue cleanup, retry failed uploads
- connected devices panel with auto-refresh
- UI per sync pair/scan, registrazione media e controlli live base
- host/client folder synchronization with conflict copies
- video file streaming and synchronized playback mode
- live screen + system audio streaming (planned in M2 hardening)

Security note: LAN join is PIN-less by design. Trust is enforced by TLS + TOFU fingerprint checks.

## Project Layout

- `app-desktop`: Compose Desktop UI
- `core-api`: shared API contracts and event models
- `core-network`: Ktor HTTPS/WSS server, discovery, trust model and API routes
- `core-transfer`: chunked file transfer and resume logic
- `core-sync`: folder sync indexing and conflict detection
- `core-media`: media session and live streaming orchestration
- `android-client`: standalone Android APK project for CI artifacts
- `packaging`: installer/update and release docs
- `docs/adr`: architecture decisions

## Build Prerequisites

- JDK 21
- Gradle 9+ (or use `./gradlew`)

## Build Performance

Configuration cache is enabled by default in `gradle.properties`.
First run stores cache; next runs reuse it and are faster.
On this stack, Gradle can still report one Kotlin plugin compatibility warning while reusing cache successfully.

## Quick Start

```bash
./gradlew :app-desktop:run
```

## CI Artifacts

GitHub Actions workflow `/Users/lorenzo/Projects/Lan-test/.github/workflows/build-executables.yml` builds and uploads:

- macOS `.dmg`
- Windows `.msi`
- Linux `.deb`
- Android `.apk`
