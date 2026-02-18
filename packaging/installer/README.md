# Installer Targets

Compose Desktop native distributions are configured for:

- macOS: DMG
- Windows: MSI (+ EXE portable fallback)
- Linux: DEB

Recommended release checklist:

1. Build artifacts on each target OS.
2. On Windows run launcher smoke test (`LanShare.exe --self-check`) before publish.
3. Sign binaries (Apple notarization, Authenticode, GPG/debsig where applicable).
4. Publish artifacts to release channel.
