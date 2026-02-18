# LanShare Desktop

LanShare Desktop is a LAN-only desktop application for macOS, Windows, and Linux.
It provides:

- automatic host discovery on LAN
- secure pairing with PIN + TLS
- drag-and-drop file and folder sharing with resume
- host/client folder synchronization with conflict copies
- video file streaming and synchronized playback mode
- live screen + system audio streaming (planned in M2 hardening)

## Project Layout

- `app-desktop`: Compose Desktop UI
- `core-api`: shared API contracts and event models
- `core-network`: Ktor HTTPS/WSS server, discovery, pairing and API routes
- `core-transfer`: chunked file transfer and resume logic
- `core-sync`: folder sync indexing and conflict detection
- `core-media`: media session and live streaming orchestration
- `packaging`: installer/update and release docs
- `docs/adr`: architecture decisions

## Build Prerequisites

- JDK 21
- Gradle 8+

## Quick Start

```bash
gradle :app-desktop:run
```
