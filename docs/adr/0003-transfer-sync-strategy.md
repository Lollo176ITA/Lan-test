# ADR 0003: Transfer and Sync Strategy

## Status
Accepted

## Context
The app must support large files and robust LAN recovery behavior.

## Decision

- Chunked file transfer at 4 MiB with SHA-256 chunk verification.
- Resume through chunk bitmap endpoint.
- Folder sync host-client with conflict copies named `name (conflict-<device>-<timestamp>)`.

## Consequences

- Better resilience on unstable local networks.
- Conflict handling avoids silent overwrite.
- Extra disk usage from conflict copy preservation.
