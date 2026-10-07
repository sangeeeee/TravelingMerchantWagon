# Minecraft 26.3 targets

Both **NeoForge and Fabric implement wagon gameplay**: assembly and dismantling,
all three cargo-box sizes, mixed materials, draft animals and driving, oriented
collisions, cargo storage and workstations, stools, moving straw-bed sleep,
cabinets, covers and canopies. Both support Carry On, Traveler's Backpack, JEI,
SwingThroughGrass, Lithium and Iris through their loader-specific adapters.
Sophisticated Backpacks is available on NeoForge only.

The target has its own `common`, `neoforge` and `fabric` modules. It uses the
Java-only `core` module, but does not compile Minecraft 1.21.1 game sources or
inherit that version's dependencies or handbook pages.

## Vanilla additions

This target supports **Pale Oak and Poplar** alongside the ten existing wagon
woods. Both have authored textures for all thirteen wooden components, material
recipes, creative-tab variants and localized names. Bamboo remains excluded from
material-aware component crafting. Wood textures retain the same pixel density
and share the existing 1024px atlas; they are not generated during play.

**Shelves** in all vanilla woods, including bamboo, work as cargo. Right-click
one of the three sections on the front to exchange its stored stack with the
stack in your main hand. Stored items remain visible. Sneak-right-click removes
the shelf as cargo and drops its contents, following normal container rules.
Shelf contents survive block/entity conversion and Carry On transfers. Shelves
use their standalone, unpowered behavior on a wagon.

All eight **copper chest** oxidation/wax variants use the cargo chest menu, lid
animation, matching hinge sounds and persistent contents. Locks and pending
loot tables are checked using the current game's data components.

Wool and dye support remains the vanilla sixteen colours. New single-block
decorations, wool stairs and slabs use ordinary cargo rendering. Native cushions
are entity-placement items and follow the existing cargo policy for entity items.

**Straw beds** replace this edition's former wagon straw-mat item. Place a vanilla
straw bed on an empty cargo slot to lay the authored wagon bedding model across
three consecutive slots in the same column, away from the player. Either end can
face forward. Right-click any part to sleep without changing your respawn point;
sneak-right-click to retrieve it. Beds on wagons are reusable after waking and
return a vanilla straw-bed item when removed or when the wagon is dismantled or
destroyed. Sleep and item ownership survive block/entity conversion. Ordinary
beds and doors remain excluded from cargo.

## Toolchain

- Minecraft 26.3 / NeoForm 26.3-1, Java 25.
- NeoForge 26.3.0.52-beta / ModDevGradle 2.0.148.
- Fabric Loader 0.19.5 / Fabric API 0.162.0+26.3 / Loom 1.17.21.
- Gradle 9.6.0 (the shared wrapper).
- GeckoLib 5.5.7 is required. Both loaders use its 5.x model and render-state API.

Minecraft is unobfuscated in 26.3. Fabric uses `net.fabricmc.fabric-loom` with
ordinary Java dependency configurations, with no intermediary remapping task,
Mojang mappings dependency or Parchment overlay. Other targets retain their own
mapping and Java settings.

## Compatibility dependency inventory

Checked against the projects' published artifacts on **2026-10-07**. The loader
properties pin exact Modrinth version IDs rather than ambiguous version names.
Optional compatibility APIs are compile-only and are not bundled in the wagon
JAR or declared as required mods. Development clients load available optional
mods by default. Servers omit Iris, Sodium and JEI.

| Mod | NeoForge 26.3 | Fabric 26.3 |
| --- | --- | --- |
| [GeckoLib](https://modrinth.com/mod/geckolib) (required) | 5.5.7 | 5.5.7 |
| [Carry On](https://modrinth.com/mod/carry-on) | 2.12.0 | 2.12.0 |
| [Traveler's Backpack](https://modrinth.com/mod/travelersbackpack) | 11.4.2 | 11.4.1 |
| [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks) | 3.26.9.2192 | No published build found |
| [Sophisticated Core](https://modrinth.com/mod/sophisticated-core) | 1.5.5.2380 | No published build found |
| [SwingThroughGrass](https://modrinth.com/mod/swing-through-grass) | 1.1.0 | 1.1.0 |
| [Architectury API](https://modrinth.com/mod/architectury-api) (required by SwingThroughGrass) | 22.0.4 | 22.0.4 |
| [Lithium](https://modrinth.com/mod/lithium) | 0.26.2 | 0.26.2 |
| [Iris](https://modrinth.com/mod/iris) | 1.11.7 | 1.11.7 |
| [Sodium](https://modrinth.com/mod/sodium) (required by Iris) | 0.9.2 | 0.9.2 |
| [JEI](https://modrinth.com/mod/jei) | 31.9.0.58 | 31.9.0.58 |
| [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) | Not needed | 26.3.1 |

JEI includes MezzConfig 0.6.6 as a nested library. Fabric's Forge Config API Port
includes NightConfig; it is a required library for Fabric wagon
configuration as well as for Traveler's Backpack. Iris uses its
explicitly required stable Sodium release rather than a newer alpha.

### Not added

No published Minecraft 26.3 builds were found for Sable, Patchouli, Touhou Little
Maid (official or Orihime), or the existing TACZ/Refabricated projects. Fabric
Sophisticated Backpacks/Core also have no matching official or existing port
build. The older Fabric-only SwingThrough project has no 26.3 build; the
SwingThroughGrass project now publishes a Fabric version and is used instead.

These integrations and their handbook content are excluded from this target.
The existing 1.20.1 and 1.21.1 editions retain them. There is no handbook item or
automatic handbook gift in 26.3 until Patchouli is available.

## Commands

```powershell
.\gradlew.bat :mc-26.3:neoforge:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.3:fabric:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.3:build
.\gradlew.bat :mc-26.3:neoforge:runClient
.\gradlew.bat :mc-26.3:fabric:runClient
```

Each loader also owns its `runServer` task. The NeoForge artifact is
`neoforge/build/libs/tm_wagon-neoforge-26.3-1.0.2.jar`. The Fabric artifact is
`fabric/build/libs/tm_wagon-fabric-26.3-1.0.2.jar`. The root `build` aggregates all versions.

Use `-PwithAllCompat=false` (NeoForge) or `-PfabricWithAllCompat=false` (Fabric)
for a minimal development runtime. Individual `with<Name>` / `fabricWith<Name>`
flags override it: `CarryOn`, `Backpacks`, `SwingThrough`, `Lithium`, `Jei`, `Iris`.
Disabling a runtime profile keeps its compile-only API available.

Build output, `libs/`, game directories and development logs remain ignored by
the repository-wide rules.

## Configuration and smoke test

Both loaders create `config/tm_wagon-server.toml` during startup. NeoForge
synchronizes server settings using FML's `SYNCED` type; Fabric uses Forge Config
API Port's `SERVER` type. Cargo
rules, driving speeds, acceleration, cargo penalties and draft-team benefits
retain the existing defaults. Configuration comments are in English.

```powershell
.\gradlew.bat :mc-26.3:neoforge:runClient -PportSmoke=true
.\gradlew.bat :mc-26.3:fabric:runClient -PportSmoke=true
.\gradlew.bat :mc-26.3:fabric:runServer -PportServerSmoke=true
```

The client smoke test creates a fresh flat world in the loader's `build/port-smoke`, and checks
three assembled sizes, reversible jack conversion and single-owner cargo,
material-sensitive crafting including Pale Oak and Poplar, 16 native cargo menus,
eight copper chests, thirteen shelf types with stack swaps and content preservation,
furnace processing and
brewing (including component fuel speeds), horse hitching and driving,
moving player sleep and repeated wake without consuming bedding or changing respawn,
three-slot straw-bed placement in both directions and intact item recovery, localized item names, Carry On chest transfer,
and available native backpack menus with contents preserved across cargo save/load.
It renders block/entity wagons and component items, captures screenshots, and
exits automatically. Test code is excluded from release JARs. It can also run
with `-PwithAllCompat=false` (NeoForge) or `-PfabricWithAllCompat=false` (Fabric)
to check optional-mod isolation. The Fabric dedicated-server smoke uses a separate
`build/server-smoke` directory and exits after registration and configuration checks.

The dedicated server has been started with available server integrations. Iris,
Sodium and JEI remain client-only. The Iris shadow adapter's target class is
validated with Iris loaded; appearance under individual shader packs still
requires visual testing with those packs enabled.

## Primary references

- [Fabric's 26.3 development notes](https://www.fabricmc.net/2026/09/15/263.html)
- [Minecraft Java Edition 26.3 additions](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)
- [Fabric 26.3 example project](https://github.com/FabricMC/fabric-example-mod/tree/26.3)
- [NeoForge 26.3 MDK](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle)
- [NeoForge version metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)
- [Fabric Loader metadata](https://meta.fabricmc.net/v2/versions/loader/26.3)
- [Modrinth version API](https://docs.modrinth.com/api/operations/getprojectversions/)
