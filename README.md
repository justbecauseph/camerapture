# Camerapture — Performance Fork

> An unofficial Minecraft 26.2 Fabric and NeoForge fork of
> [Camerapture](https://github.com/chrrs/camerapture), focused on large picture displays, distant rendering,
> server scalability, and safer image handling.

![Fork build status](https://img.shields.io/github/actions/workflow/status/justbecauseph/camerapture/build.yml?branch=26.2&style=flat-square)
[![Upstream Modrinth](https://img.shields.io/modrinth/dt/9dzLWnmZ?style=flat-square&logo=modrinth&label=upstream%20Modrinth)](https://modrinth.com/mod/camerapture)
[![Upstream CurseForge](https://img.shields.io/curseforge/dt/1051342?style=flat-square&logo=curseforge&label=upstream%20CurseForge)](https://curseforge.com/minecraft/mc-mods/camerapture)

Camerapture adds cameras, photographs, albums, and placeable picture frames to Minecraft. This repository keeps
the original gameplay while replacing the picture-frame and image-delivery internals for worlds with large
displays and many concurrent players.

This is not an official upstream release. The Modrinth and CurseForge links above lead to the original mod and do
not include the fork-specific changes described below. See [CHANGELOG.md](CHANGELOG.md) for the detailed 2.0.0
change list.

## What differs from upstream

### Block-based picture frames

- Picture frames are block entities instead of ticking entities, removing picture-frame entity overhead from the
  server main thread.
- A single stationary anchor block owns each resizable frame. Resizing expands or shrinks the frame around that
  anchor instead of moving its attachment point.
- Dynamic selection/interaction shapes and render bounds follow frames up to 16×16 blocks.
- The picture-frame menu, persisted state, and Jade inspection now target the block entity.

### Rendering for large and distant displays

- Full-frame frustum bounds fix large pictures disappearing when their 1×1 anchor leaves the visible chunk
  section.
- Screen-space LOD selects full textures, thumbnails, or skips subpixel frames according to their actual projected
  size. Hysteresis prevents flickering near quality thresholds.
- Separate full-resolution and thumbnail texture caches use VRAM byte budgets instead of entry counts. A thumbnail
  remains visible while the full texture loads and acts as a fallback after full-texture eviction.
- Optional Distant Decorations integration keeps picture frames represented beyond the normal loaded-chunk render
  path.
- An optional client setting adds a textured rear surface and physical frame edges. It is disabled by default to
  preserve Camerapture's original flat-picture appearance.

### Multiplayer and dense-area scalability

- Picture file reads run off the server thread on a bounded daemon worker pool.
- Per-player round-robin download queues prevent one player or picture from monopolizing delivery.
- Concurrent requests for the same picture are deduplicated, and the server cache is bounded by bytes with LRU
  eviction.
- Client disk caching is enabled by default, avoiding repeated downloads across multiplayer sessions.

### Thumbnail delivery and image safety

- The server creates 128-pixel WebP thumbnails on upload and lazily creates them for pictures from existing worlds.
- Network requests identify full and thumbnail quality independently, with recovery for failed or interrupted
  transfers.
- WebP headers are validated before decoding. Server and client checks enforce compressed-byte and resolution
  limits for lossy, lossless, and extended WebP files.
- Client image conversion uses bulk pixel reads to reduce upload-processing cost.

### Configuration and diagnostics

The Cloth Config screen includes controls for distant rendering, LOD thresholds, full and thumbnail VRAM budgets,
disk caching, and 3D picture-frame backing. The principal fork defaults are:

| Setting | Default |
| --- | ---: |
| Distant picture rendering | Enabled |
| Full-texture VRAM budget | 512 MiB |
| Thumbnail VRAM budget | 64 MiB |
| Server thumbnail resolution | 128 px |
| Local multiplayer picture cache | Enabled |
| 3D frame backing and edges | Disabled |

Internal telemetry tracks extraction and culling decisions, requested and rendered texture quality, cache activity,
VRAM residency, placeholders, and network volume for performance diagnosis.

## Compatibility

- Minecraft 26.2
- Fabric and NeoForge
- Java 25
- Optional: Cloth Config and Mod Menu for the configuration screen
- Optional: Distant Decorations for far-distance picture representation

Both the server and clients should use this fork because its picture-quality packets, thumbnails, configuration,
and block-based frame data differ from upstream Camerapture 1.x.

## Project structure

Camerapture supports Minecraft versions using branches. This fork's maintained work is on `26.2`. Branches prefixed
with `old/` are inherited historical versions and are not maintained here.

### Update checklist

- Create a new branch based on the latest branch.
- Update the Minecraft version in `gradle.properties` and update all dependencies.
- Fix conflicts and test on both loaders.
- Update this README and `CHANGELOG.md` when fork-specific behavior changes.

### Release checklist

- Update `mod.version` in `gradle.properties`.
- Add an entry to `CHANGELOG.md`.
- Commit and push a version tag, prefixed with `v` (for example, `v2.0.0`).
- Manually trigger the Publish workflow for each maintained version.

## Credits

- [chrrs](https://github.com/chrrs) and the Camerapture contributors for the
  [original mod](https://github.com/chrrs/camerapture).
- henkelmax for making the [Camera Mod for Forge](https://modrinth.com/mod/camera-mod), the main inspiration for
  Camerapture.
- Everyone on the original project's Origins server for their feedback and patience.

This project remains available under the [MIT License](LICENSE).
