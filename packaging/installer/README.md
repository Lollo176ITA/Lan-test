# Installer Targets

Compose Desktop native distributions are configured for:

- macOS: DMG
- Windows: MSI
- Linux: DEB

Recommended release checklist:

1. Build artifacts on each target OS.
2. Sign binaries (Apple notarization, Authenticode, GPG/debsig where applicable).
3. Publish artifacts to release channel.
