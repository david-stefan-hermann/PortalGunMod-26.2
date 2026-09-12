# PortalGunMod – Minecraft 26.2 port

An unofficial port of **PortalGunMod** by **ButterBoyRS** to Fabric for Minecraft 26.2.

- Original mod: https://modrinth.com/mod/portalblaster (version 1.2.0, Fabric, Minecraft 1.21.11, MIT License)
- All gameplay, assets and design are ButterBoyRS's work. This repository only adapts the code to the 26.2 API.
- If the original author releases a 26.x version, use that instead.

## What the mod does

Blue and orange portals on walls, floors and ceilings, momentum-preserving travel for players, mobs and projectiles, a see-through preview of the other side (a raycast colour mosaic), grabbing and throwing mobs and blocks (G), clearing your portals (V), night-time "moon portals" with suction, a pedestal block, first-person animations, HUD reticle brackets and custom sounds.

## Differences from the original

- Targets Minecraft 26.2, Fabric Loader 0.19.5+, Fabric API 0.160.0+26.2, Java 25.
- Source was recovered by decompiling the 1.2.0 jar (no public repository exists) and rewriting it to Mojang names. The unmodified decompilation, symbol reports and tools live in `upstream/`.
- 26.2 removed `ItemDisplayContext` from special item model renderers. The first-person recoil and shake animations are now selected in `assets/portalgun/items/portal_gun.json` via `minecraft:display_context` cases that pass `first_person`/`left_hand` flags to the renderer.

Mod id stays `portalgun`; never load this jar together with the original.

## Build

```bash
./gradlew build
```

The jar is written to `build/libs/portalgun-<version>.jar`. Requires JDK 25.

## Installation

Put the jar and Fabric API into the `mods` folder of a Fabric 26.2 instance.

## License

MIT, see `LICENSE`. Copyright ButterBoyRS; port changes by David Stefan Hermann.
