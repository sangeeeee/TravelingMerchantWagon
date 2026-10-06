# Fabric 1.21.1

This target has a Fabric Loom development environment and isolated Fabric
compatibility dependencies. Gameplay registrations, platform bindings and
compatibility implementations have not been ported yet. Packaging tasks remain
disabled, so this preparation does not produce an installable Fabric mod.

The build compiles the sibling `common` sources with official Mojang mappings
and consumes the Java-only `core`. NeoForge artifacts are never used on this
target's compatibility classpath. Local development JARs belong in this target's
ignored `libs/` directory.

## Required development dependencies

- Fabric Loom 1.17.21, JDK 21 and the repository's Gradle 9.5.0 wrapper.
- Fabric Loader 0.18.4 and Fabric API 0.116.17+1.21.1.
- GeckoLib 4.9.3 for Fabric.

## Compatibility library inventory

The following Fabric 1.21.1 releases were checked against their published
`fabric.mod.json` and project metadata. Exact Modrinth version IDs are pinned in
`gradle.properties`; a shared release number cannot accidentally select a
NeoForge artifact. Adding a dependency does not itself port a compatibility hook.

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
the parent mods provide them at runtime. Veil is supplied by Sable rather than
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

The first command resolves the compile APIs and compiles shared game code. The
second also resolves all selected optional runtime dependencies. Neither command
claims that gameplay integration or combined-mod startup has been implemented.

Individual development profiles use `-PfabricWith<Name>=true`: `Maid`,
`Backpacks`, `CarryOn`, `Sable`, `Patchouli`, `Guns`, `SwingThrough`, `Lithium`,
`Iris`, or `Jei`. These flags affect only the Fabric target. Optional mods remain
compile-only by default and are never bundled into the wagon mod.
