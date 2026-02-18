# Implementation Status

## Delivered in this iteration

- Multi-module Kotlin/Gradle project with Compose Desktop app.
- Shared API contracts for all planned endpoints/events.
- HTTPS/WSS server with LAN join flow (TLS + TOFU fingerprint), device list, events stream.
- Transfer API with chunk upload/download/resume/complete and hash verification.
- Sync API with host-client pair registration and conflict detection.
- Media registration, byte-range media file streaming, playback sessions.
- Live session lifecycle endpoints and capability checks.
- Desktop UI in Italian with auto-discovery, quick connect, drag-and-drop/picker upload, queue controls, sync/media/live panels.
- Packaging/update/ADR docs scaffold.

## Remaining hardening work (M2+)

- Production-grade FFmpeg process orchestration for live stream.
- Full SQLDelight query integration in runtime persistence layer.
- Cross-platform installer signing/notarization pipelines.
- Automatic update download/apply flow in desktop client.
- End-to-end interoperability tests on macOS/Windows/Linux.
