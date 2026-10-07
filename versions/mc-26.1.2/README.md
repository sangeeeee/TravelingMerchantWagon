# Minecraft 26.1.2 targets

**NeoForge is playable; Fabric remains a dependency scaffold.** The NeoForge
edition includes wagon assembly, all three cargo-box sizes, driving, cargo and
workstations, accessories, material variants, recipes and optional integrations.
The Fabric entry point is reserved for the subsequent gameplay port.

## Layout and toolchains

```text
versions/mc-26.1.2/
  gradle.properties
  compatibility.gradle
  common/      version-specific geometry, gameplay and resources
  neoforge/    playable NeoForge implementation and compatibility adapters
  fabric/      Fabric loader entry point and dependencies
```

- Minecraft **26.1.2**, NeoForm **26.1.2-1**, Java **25**.
- NeoForge **26.1.2.114**, FML **11**, ModDevGradle **2.0.148**.
- Fabric Loader **0.19.5**, Fabric API **0.155.3+26.1.2**, Loom **1.17.21**.
- Shared Gradle wrapper **9.6.0**; shared mod version **1.0.1**.

Minecraft 26.1.2 is unobfuscated. Fabric uses `net.fabricmc.fabric-loom`
without a mappings or remapping dependency. Java 25 is selected only for this
target; the older targets keep their own toolchain settings. Local JDK paths
belong in the ignored `gradle-local.properties` file.

## Dependency inventory

Checked on **2026-10-07** using Modrinth's 26.1.2 version lists, the authors'
GitHub releases and downloaded JAR metadata. Loader properties pin exact
artifact IDs or author release tags. Stable releases are preferred; Patchouli
and the maid ports currently require the beta/snapshot artifacts shown here.

| Mod | NeoForge 26.1.2 | Fabric 26.1.2 |
| --- | --- | --- |
| GeckoLib | [5.5.2](https://modrinth.com/mod/geckolib/version/xfVfPcoC) | [5.5.2](https://modrinth.com/mod/geckolib/version/XZTmZlwb) |
| Carry On | [2.10.0](https://modrinth.com/mod/carry-on/version/c2uJiLoV) | [2.10.0](https://modrinth.com/mod/carry-on/version/ISFd6ie5) |
| Traveler's Backpack | [26.1.2-11.2.8](https://modrinth.com/mod/travelersbackpack/version/glTGAXmg) | [26.1.2-11.2.11](https://modrinth.com/mod/travelersbackpack/version/NUbnGgy9) |
| Sophisticated Backpacks | [26.1.2-3.26.9.2193](https://modrinth.com/mod/sophisticated-backpacks/version/thjFLz7b) | No matching published artifact found |
| Sophisticated Core | [26.1.2-1.5.7.2377](https://modrinth.com/mod/sophisticated-core/version/MKrSdHBA) | No matching published artifact found |
| SwingThroughGrass | [1.1.0](https://modrinth.com/mod/swing-through-grass/version/aq7pfQB8) | [1.1.0](https://modrinth.com/mod/swing-through-grass/version/VvhTqd2K) |
| Architectury API | [20.1.16+neoforge](https://modrinth.com/mod/architectury-api/version/9PW5lWrQ) | [20.1.16+fabric](https://modrinth.com/mod/architectury-api/version/Y7kRthh0) |
| Lithium | [mc26.1.2-0.24.7-neoforge](https://modrinth.com/mod/lithium/version/eZ0KJiEA) | [mc26.1.2-0.24.7-fabric](https://modrinth.com/mod/lithium/version/Oqq8TOAV) |
| JEI | [29.34.0.90](https://modrinth.com/mod/jei/version/AisPQmQz) | [29.34.0.90](https://modrinth.com/mod/jei/version/jZ7OgHsw) |
| Iris | [1.11.4+26.1-neoforge](https://modrinth.com/mod/iris/version/qE5Y7GrZ) | [1.11.4+26.1-fabric](https://modrinth.com/mod/iris/version/sZbVsl2Q) |
| Sodium | [mc26.1.2-0.9.2-neoforge](https://modrinth.com/mod/sodium/version/zg4YQ9EL) | [mc26.1.2-0.9.2-fabric](https://modrinth.com/mod/sodium/version/tZQ3jqnf) |
| Forge Config API Port | Not needed | [26.1.5](https://modrinth.com/mod/forge-config-api-port/version/jUe0ucoE) |
| Patchouli | [26.1-94-beta](https://modrinth.com/mod/patchouli/version/2CsnFLom) | [26.1-94-beta](https://modrinth.com/mod/patchouli/version/AveV4Tjn) |
| Touhou Little Maid | [2.0.0 snapshot, 2026-08-24](https://github.com/TouhouLittleMaid/TouhouLittleMaid-26.1/releases/tag/snapshot-2026-08-24-14-04-03) | [Tsumugi 1.0.23-beta.1](https://github.com/gege-tlph/TouhouLittleMaid-Tsumugi/releases/tag/v1.0.23-beta.1%2Bmc26.1.2) |
| TaCZ | No matching NeoForge artifact found | [Unofficial Refabricated 1.1.8 R3-hotfix2](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases/tag/26.1.2%E7%9A%84mod%E6%96%87%E4%BB%B6_R3_HOTFIX-2) |

GeckoLib is required by the wagon mod. Fabric API and Forge Config API
Port are also required on Fabric. Other integrations are compile-only APIs,
with separate development-runtime profiles; they are not bundled in the wagon
JAR or declared mandatory for players.

Patchouli **26.1-94-beta** explicitly supports the 26.1 release line including
26.1.2 on both loaders. The NeoForge edition includes the illustrated handbook, its custom recipe
pages and the configurable one-time login gift. Without Patchouli, the handbook
item and its recipe are not registered. Fabric handbook integration is pending.

NeoForge's maid artifact is an official development snapshot. The Fabric maid
uses **Tsumugi**, a community continuation of Orihime with an available 26.1.2
release. Fabric TaCZ uses the separate **Unofficial Refabricated** project.
These GitHub-only artifacts resolve automatically through loader-specific,
artifact-only Ivy repositories with fixed tags and original installable JAR
names. They are not downloaded into a shared cross-version library directory.

## Required support libraries

- Architectury is required by SwingThroughGrass on both loaders.
- Sophisticated Core is required by NeoForge Sophisticated Backpacks.
- Fabric Traveler's Backpack, Tsumugi and TaCZ require Fabric API and Forge
  Config API Port; the pinned versions meet their declared minimums.
- The selected Iris releases require the exact pinned Sodium releases.
- TaCZ's BCEL, Commons Math, LuaJ and MAE libraries are already nested in its
  installable JAR. Forge Config API Port carries its NightConfig libraries.
- Mod Menu, Cloth Config and Cardinal Components are not added: none of the
  chosen releases declares them as a mandatory external dependency.

## Unavailable integrations

No matching Sable 26.1.2 release was found. The existing Fabric Sophisticated
Backpacks/Core ports also publish no matching artifact, so only NeoForge gets
these dependencies. Orihime itself and the original TaCZ Refabricated Modrinth
project have no matching 26.1.2 files; the author releases listed above provide
the available successor ports. No unrelated 1.21.1 or 26.2 JAR is substituted.

## Commands and runtime profiles

```powershell
.\gradlew.bat :mc-26.1.2:build
.\gradlew.bat :mc-26.1.2:neoforge:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.1.2:fabric:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.1.2:neoforge:runClient
.\gradlew.bat :mc-26.1.2:fabric:runClient
.\gradlew.bat :mc-26.1.2:neoforge:runServer
.\gradlew.bat :mc-26.1.2:fabric:runServer
```

Launch tasks belong to each loader's `Tasks > minecraft` group. The root and
version parent expose aggregate build tasks, without game-launch aliases.
Both targets write their JAR to their own `build/libs/`, named
`tm_wagon-<loader>-26.1.2-1.0.1.jar`. The root aggregate automatically includes
this version; no global loader list or existing target settings are changed.

Development runs load all available integrations by default. Iris, Sodium and
JEI enter client launches only, keeping dedicated-server classpaths free of
those client-only integrations. Run directories, downloads, build products and
logs are covered by the existing Git ignore rules.

Use `-PwithAllCompat=false` on NeoForge or `-PfabricWithAllCompat=false` on
Fabric for a minimal runtime. Individual flags are `with<Name>` or
`fabricWith<Name>` for `CarryOn`, `Backpacks`, `SwingThrough`, `Lithium`, `Jei`,
`Iris`, `Patchouli` and `Maid`; Fabric additionally supports `Tacz`.
Flags control development runtime only; the compile APIs remain available.

## Verification

The NeoForge checks cover a real client world and a dedicated server, both
with the available compatibility mods installed. The opt-in client smoke checks
assembly and animated form changes, cargo contents, material-sensitive recipes,
workstation cooking and menus, straw-mat sleep, mule/camel hitching, Carry On,
both backpack bridges, maid tasks and the Patchouli reader. Test helpers are
excluded from published JARs.

## Version-specific gameplay

- Wood variants match 26.1.2: oak, spruce, birch, jungle, acacia, dark oak,
  mangrove, cherry, pale oak, crimson and warped. Bamboo is excluded.
- Reusable wagon straw mats reserve three cargo slots and do not change the
  player's respawn point. The newer vanilla straw bed is not referenced.
- Copper chests and shelves work as cargo, alongside the supported containers
  and standalone workstations from earlier editions.
- Carry On, Traveler's Backpack and Sophisticated Backpacks retain their native
  storage interfaces and contents through loading and unloading.
- Little Maid has Wagon Companion and Wagon Passenger tasks, bow/crossbow
  combat from the companion seat, rest-time mat sleep under any task, and native
  photo/soul-sign capture and release. The maid API is adapted to its 2.0 manager
  and brain interfaces. Sable and NeoForge TaCZ are absent on this target.
- Client rendering supports the selected Iris/Sodium versions. Optional mods
  are not bundled; the minimal profile runs with GeckoLib alone.

## Primary references

- [NeoForge 26.1.2 MDK](https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle).
- [Fabric 26.1 development notes](https://fabricmc.net/2026/03/14/261.html).
- [Fabric Loader metadata](https://meta.fabricmc.net/v2/versions/loader/26.1.2).
- [Modrinth version API](https://docs.modrinth.com/api/operations/getprojectversions/).
- [Tsumugi source and dependency requirements](https://github.com/gege-tlph/TouhouLittleMaid-Tsumugi).
