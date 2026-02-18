# ADR 0001: Kotlin + Compose Desktop

## Status
Accepted

## Context
The app must run on macOS, Windows, and Linux with native installers and a single codebase.

## Decision
Use Kotlin/JVM with Compose Desktop for UI and Ktor for local HTTPS/WSS networking.

## Consequences

- Fast implementation on JVM stack.
- Strong integration with coroutines and serialization.
- Native packaging supported through Compose Desktop plugin.
