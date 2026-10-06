# Fabric 1.21.1

This target implements wagon assembly, driving, rotated collisions, cargo, seats,
sleeping, optional equipment, materials, recipes, sounds and the handbook on Fabric.
Platform events, networking and native menus use Fabric APIs. Shared gameplay
and assets are compiled with each loader's own compatibility dependencies.

Players need **Fabric API**, **GeckoLib**, and **Forge Config API Port**. Optional
integrations work with the Fabric releases listed below. Server configuration
uses `tm_wagon-server.toml`, generated at startup.

The build compiles the sibling `common` sources with official Mojang mappings
and consumes the Java-only `core`. NeoForge artifacts are never used on this
target's compatibility classpath. Local development JARs belong in this target's
ignored `libs/` directory.

## Finding wagon items in game

Launch from `mc-1.21.1 > fabric > Tasks > minecraft > runClient`.
The root project aggregates builds across versions and has no game-launch tasks.
Loom's remaining `fabric`
task category contains tooling tasks, not a second Fabric project. Reload the
Gradle project to refresh task categories after build-script changes.

Fabric places custom creative inventory tabs on additional pages. Click the
small right arrow at the upper right of the creative inventory to reach the
second page, then select the wagon icon. Wagon items also appear in creative
search. There is no numbered page indicator: the arrows are inside the inventory
panel, beside the category title. The optional Mod Menu mod supplies a main-menu mod list; Fabric does
not provide that screen by itself.

## Required development dependencies

- Fabric Loom 1.17.21, JDK 21 and the repository's Gradle 9.5.0 wrapper.
- Fabric Loader 0.18.4 and Fabric API 0.116.17+1.21.1.
- GeckoLib 4.9.3 for Fabric.

## Compatibility library inventory

The following Fabric 1.21.1 releases were checked against their published
`fabric.mod.json` and project metadata. Exact Modrinth version IDs are pinned in
`gradle.properties`; a shared release number cannot accidentally select a
NeoForge artifact. Their compatibility adapters are implemented in this target or shared gameplay sources.

| Compatibility target | Selected Fabric version | Required support libraries / port notes |
| --- | --- | --- |
| [Carry On](https://modrinth.com/mod/carry-on) | 2.2.6.13 | Meets the supported minimum of 2.2.4; requires Loader 0.18.4+ |
| [Touhou Little Maid: Orihime](https://modrinth.com/mod/touhoulittlemaid-orihime) | 0.8.2-neo1.5.3+mc1.21.1 | Unofficial beta port; Forge Config API Port; embeds Cardinal Components and Nashorn |
| [Traveler's Backpack](https://modrinth.com/mod/travelersbackpack) | 10.1.39 | Cloth Config and Cardinal Components (base/entity) |
| [Sophisticated Backpacks, unofficial port](https://modrinth.com/mod/sophisticated-backpacks-(unoffical-fabric-port)) | 3.23.4.3.106 | The Fabric Sophisticated Core port |
| [Sophisticated Core, unofficial port](https://modrinth.com/mod/sophisticated-core-(unofficial-fabric-port)) | 1.2.9.21.168 | Forge Config API Port; embeds Porting Lib and Energy |
| [Sable](https://modrinth.com/mod/sable) | 2.0.6 | Embeds Companion 1.6.0, Veil 4.3.2, Forge Config API Port and native libraries |
| [Patchouli](https://modrinth.com/mod/patchouli) | 1.21.1-93-fabric | Fabric API |
| [TaCZ: Refabricated](https://modrinth.com/mod/tacz-refabricated) | 0.7.1-forge1.1.8-hotfix2 | Unofficial beta port; Forge Config API Port; embeds Cardinal Components |
| [SwingThrough](https://modrinth.com/mod/swingthrough) | 1.0.5+1.21 | Fabric counterpart, with different hooks from SwingThroughGrass |
| [Lithium](https://modrinth.com/mod/lithium) | 0.15.4 | Fabric artifact |
| [Iris](https://modrinth.com/mod/iris) | 1.8.8 | Sodium **0.6.x**; pinned to 0.6.13 |
| [JEI](https://modrinth.com/mod/jei) | 19.51.0.418 | Fabric API |

External support libraries are pinned to Forge Config API Port 21.1.6, Cloth
Config 15.0.140 and Cardinal Components 6.1.3. Only required external libraries
are added to the corresponding development runtime. Embedded Porting Lib,
Energy, Cardinal Components and Sable Companion APIs are extracted and remapped for compilation;
the parent mods provide them at runtime. For development runs, an artifact transform
restores nested mod and library archives that Loom otherwise strips during remapping. Veil is supplied by Sable rather than
replaced with an unrelated newer standalone release.

The native NeoForge Touhou Little Maid, Sophisticated Backpacks/Core, TACZ and
SwingThroughGrass dependencies are replaced by the Fabric targets above, not
carried over. Generic mod-container support (including BetterEnd/BCLib barrels)
does not require those mods' APIs, so no extra hard dependency is introduced.

## Dependency verification and optional runtime profiles

From the repository root:

```powershell
.\gradlew.bat :mc-1.21.1:fabric:build
.\gradlew.bat :mc-1.21.1:fabric:verifyCompatibilityDependencies -PfabricWithAllCompat=true
```

The first command builds the release JAR and runs the base gameplay tests. The
second checks the API and optional runtime classpaths. For actual integration tests
and renderer previews:

```powershell
.\gradlew.bat :mc-1.21.1:fabric:runGameTest -PfabricWithAllCompat=true
.\gradlew.bat :mc-1.21.1:fabric:runClient -PfabricSmoke=true -PfabricWithAllCompat=true
.\gradlew.bat :mc-1.21.1:fabric:runClient -PfabricSmoke=true -PfabricWithIris=true
.\gradlew.bat :mc-1.21.1:fabric:runClient -PfabricSmoke=true -PfabricSmokeMode=backpacks -PfabricWithBackpacks=true
.\gradlew.bat :mc-1.21.1:fabric:runClient -PfabricSmoke=true -PfabricSmokeMode=creative
.\gradlew.bat :mc-1.21.1:fabric:runClientSmokeTest
```

The loader's `runClientSmokeTest` task uses the isolated `build/client-smoke`
directory. Preview code compiles separately and is never packaged in the release
JAR, including when a preview flag is supplied.

The creative preview can use a disposable save in the selected run's `saves` with
`-PfabricSmokeWorld=<folder-name>`; it opens and saves that world. Use a copy
when investigating an existing save.

Sable 2.0.6 declares an incompatibility with the Sodium 0.6.x required by Iris
1.8.8. The all-compatibility development profile therefore uses Sable without
Iris/Sodium; the separate Iris profile validates those graphics dependencies.
This profile choice does not restrict which optional mods the release JAR detects.

Individual development profiles use `-PfabricWith<Name>=true`: `Maid`,
`Backpacks`, `CarryOn`, `Sable`, `Patchouli`, `Guns`, `SwingThrough`, `Lithium`,
`Iris`, or `Jei`. These flags affect only the Fabric target. Optional mods remain
compile-only by default and are never bundled into the wagon mod.

The `backpacks` preview mode opens the isolated `run/saves/repro` fixture world
and validates native menus and networked slot/settings edits. Supply a disposable
fixture save before that run; it never targets an external modpack world.

The `creative` preview uses the same disposable fixture save to check the real
creative inventory, its second-page wagon tab, and inclusion in creative search.
