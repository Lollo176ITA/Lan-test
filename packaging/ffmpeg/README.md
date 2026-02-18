# FFmpeg Bundling

The live streaming module expects FFmpeg at:

- `~/.lanshare/host/bin/ffmpeg`

For production packaging:

1. Bundle FFmpeg executable per OS.
2. Ensure executable permissions on macOS/Linux.
3. Document codec licensing obligations for your distribution.
