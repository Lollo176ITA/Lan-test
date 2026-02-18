# ADR 0002: LAN Security Model

## Status
Accepted

## Context
LanShare must work in LAN-only mode without mandatory cloud dependencies.

## Decision
Security model includes:

- self-signed TLS certificate generated on first host run
- no PIN requirement for LAN join
- TOFU fingerprint check on client side

## Consequences

- Better security than plain local HTTP.
- Zero-friction onboarding for LAN users.
- Fingerprint mismatch blocks connection and requires user intervention.
