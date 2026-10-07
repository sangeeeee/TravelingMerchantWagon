# Minecraft 26.3 port environment

This target currently provides build scripts, loader entry points and dependency
inventories. **Wagon gameplay and compatibility implementations have not been
ported yet.** The generated JARs are development scaffolds, not release builds.

The target has its own `common`, `neoforge` and `fabric` modules. It uses the
Java-only `core` module, but does not compile Minecraft 1.21.1 game sources or
inherit that version's dependencies or handbook pages.

## Toolchain

- Minecraft 26.3 / NeoForm 26.3-1, Java 25.
- NeoForge 26.3.0.52-beta / ModDevGradle 2.0.148.
- Fabric Loader 0.19.5 / Fabric API 0.162.0+26.3 / Loom 1.17.21.
- Gradle 9.6.0 (the shared wrapper).
- GeckoLib 5.5.7 is required on both loaders. Its 4.x integration needs porting.

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
includes NightConfig; it is a required library for the upcoming wagon
configuration implementation as well as for Traveler's Backpack. Iris uses its
explicitly required stable Sodium release rather than a newer alpha.

### Not added

No published Minecraft 26.3 builds were found for Sable, Patchouli, Touhou Little
Maid (official or Orihime), or the existing TACZ/Refabricated projects. Fabric
Sophisticated Backpacks/Core also have no matching official or existing port
build. The older Fabric-only SwingThrough project has no 26.3 build; the
SwingThroughGrass project now publishes a Fabric version and is used instead.

These libraries and their old Mixins, integrations and handbook entries must
not be copied into this target. While porting, remove unsupported integration
references from this target's documentation/resources only. Keep the existing
1.20.1 and 1.21.1 editions intact. Patchouli's absence also means that this target
must not register or distribute a Patchouli handbook until support is available.

## Commands

```powershell
.\gradlew.bat :mc-26.3:neoforge:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.3:fabric:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.3:build
.\gradlew.bat :mc-26.3:neoforge:runClient
.\gradlew.bat :mc-26.3:fabric:runClient
```

Each loader also owns its `runServer` task. Release-style scaffold filenames
include the loader and game version and are generated in that module's
`build/libs/`. The root `build` aggregates all versions.

Use `-PwithAllCompat=false` (NeoForge) or `-PfabricWithAllCompat=false` (Fabric)
for a minimal development runtime. Individual `with<Name>` / `fabricWith<Name>`
flags override it: `CarryOn`, `Backpacks`, `SwingThrough`, `Lithium`, `Jei`, `Iris`.
Disabling a runtime profile keeps its compile-only API available.

Build output, `libs/`, game directories and development logs remain ignored by
the repository-wide rules.

## Primary references

- [Fabric's 26.3 development notes](https://www.fabricmc.net/2026/09/15/263.html)
- [Fabric 26.3 example project](https://github.com/FabricMC/fabric-example-mod/tree/26.3)
- [NeoForge 26.3 MDK](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle)
- [NeoForge version metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)
- [Fabric Loader metadata](https://meta.fabricmc.net/v2/versions/loader/26.3)
- [Modrinth version API](https://docs.modrinth.com/api/operations/getprojectversions/)
