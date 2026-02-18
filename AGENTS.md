# LanShare Agent Notes

## Packaging Rules (Do Not Regress)

1. Compose Desktop `nativeDistributions.packageVersion` must be `MAJOR.MINOR.PATCH` with `MAJOR > 0`.
2. For macOS DMG, always set both:
   - `nativeDistributions.macOS.packageVersion`
   - `nativeDistributions.macOS.dmgPackageVersion`
3. Do not bind DMG package version to project `0.x.y` versions.
4. Keep Windows packaging with runtime included (`includeAllModules = true`) and keep CI launcher smoke test (`LanShare.exe --self-check`).
