# Upstream material for the PortalGunMod 26.2 port

Source: **PortalGunMod** by ButterBoyRS, Modrinth slug `portalblaster`, version 1.2.0 (2026-09-04), MIT License, Fabric, Minecraft 1.21.11.
Download: https://modrinth.com/mod/portalblaster/version/1.2.0 (`portalgun-1.2.0.jar`, SHA-1 `cb8784e11b7b8097de12f0d240ed3de231f0f818`).

| Folder | Content |
|---|---|
| `portalgun-1.2.0.jar` | the original jar (compiled against intermediary names) |
| `src-named/` | Vineflower 1.12.0 decompilation, rewritten to Mojang names (1.21.11) with `tools/RemapSource.java` |
| `resources/` | assets, data, `fabric.mod.json`, `portalgun.mixins.json` extracted from the jar |
| `reports/` | every Minecraft class/method/field the mod uses, its Mojang name and whether it exists in the 26.2 jar; `MAPPING-TABLE.tsv` = intermediary → Mojang for all used symbols |
| `tools/` | `MapCheck.java` (symbol check against 26.2), `RemapSource.java` (intermediary → Mojang text rewrite) |

The port plan is `../../HANDOFF-OPUS-PORTALGUN-PLAN.md`. Generated 2026-09-12.
