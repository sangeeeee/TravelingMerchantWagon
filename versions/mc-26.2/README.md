# Minecraft 26.2 development targets

**NeoForge and Fabric gameplay are implemented.** The port provides assembly, three wagon
sizes, mixed wood and dyed components, cargo workstations, passenger seating,
bedding, draft animals, driving and optional compatibility adapters.

The 26.2 wood inventory includes pale oak and excludes bamboo components and
26.3's poplar. Native straw beds do not exist in 26.2, so this target uses the
craftable wagon straw mat: three longitudinal cargo cells, either direction,
repeatable sleep, no respawn point and one mat returned on removal or dismantling.
Copper chests and all 26.2 shelf woods are supported. Furnaces, smokers, blast
furnaces and brewing stands follow 26.2's native fuel and recipe rules.

This version has independent `common`, `neoforge` and `fabric` subprojects.
Dependencies and game files remain inside their own version and loader scopes.
The repository automatically discovers these projects and includes them in
root and version-level aggregate builds. Existing targets keep their settings.

## Toolchain

- Minecraft 26.2 / NeoForm 26.2-2 / Java 25.
- NeoForge 26.2.0.88 / FML 11 / ModDevGradle 2.0.148.
- Fabric Loader 0.19.5 / Fabric API 0.161.0+26.2 / Loom 1.17.21.
- Shared Gradle wrapper 9.6.0; shared mod version 1.0.1.

Minecraft 26.2 is unobfuscated. Fabric uses `net.fabricmc.fabric-loom` and
standard Java dependency configurations without Mojang or intermediary mappings.
Java 25 is selected per target; lower-version targets retain their own JDKs.
Local JDK locations belong in the ignored `gradle-local.properties` file.

## Published dependency inventory

Checked on **2026-10-07** against Modrinth version metadata and the downloaded
JAR manifests. Each loader's properties pin an exact version ID. Release builds
are preferred. Iris uses its explicitly required stable Sodium version.

| Mod | NeoForge 26.2 | Fabric 26.2 |
| --- | --- | --- |
| GeckoLib | [5.5.6](https://modrinth.com/mod/geckolib/version/IEGPh4CJ) | [5.5.5](https://modrinth.com/mod/geckolib/version/7gaQHok7) |
| Carry On | [2.11.2](https://modrinth.com/mod/carry-on/version/EMd9Jyxc) | [2.11.2](https://modrinth.com/mod/carry-on/version/fuV8BHlz) |
| Traveler's Backpack | [26.2-11.3.4](https://modrinth.com/mod/travelersbackpack/version/EMeTJkWH) | [26.2-11.3.3](https://modrinth.com/mod/travelersbackpack/version/I6Fg8UrJ) |
| Sophisticated Backpacks | [26.2-3.26.9.2191](https://modrinth.com/mod/sophisticated-backpacks/version/QfSnSqMl) | No matching build found |
| Sophisticated Core | [26.2-1.5.5.2379](https://modrinth.com/mod/sophisticated-core/version/vjuYjD8F) | No matching build found |
| SwingThroughGrass | [1.1.0](https://modrinth.com/mod/swing-through-grass/version/dPFxlxme) | [1.1.0](https://modrinth.com/mod/swing-through-grass/version/8RuhZHJL) |
| Architectury API | [21.1.11+neoforge](https://modrinth.com/mod/architectury-api/version/huNlZW4Q) | [21.1.11+fabric](https://modrinth.com/mod/architectury-api/version/w3CUWoxd) |
| Lithium | [mc26.2-0.25.3-neoforge](https://modrinth.com/mod/lithium/version/J9CowDXK) | [mc26.2-0.25.3-fabric](https://modrinth.com/mod/lithium/version/f7vZ0VWU) |
| JEI | [30.29.0.201](https://modrinth.com/mod/jei/version/wTO6kl61) | [30.29.0.201](https://modrinth.com/mod/jei/version/d3HeviSL) |
| Iris | [1.11.4+26.2-neoforge](https://modrinth.com/mod/iris/version/k55HdONq) | [1.11.4+26.2-fabric](https://modrinth.com/mod/iris/version/gxZWWnKH) |
| Sodium | [mc26.2-0.9.2-neoforge](https://modrinth.com/mod/sodium/version/DmnNKsfS) | [mc26.2-0.9.2-fabric](https://modrinth.com/mod/sodium/version/xJZxADzI) |
| Forge Config API Port | Not needed | [26.2.1](https://modrinth.com/mod/forge-config-api-port/version/rSd3GiG8) |

GeckoLib is a required dependency on both loaders. Fabric API and Forge Config
API Port are required for the Fabric target. Other compatibility APIs use
`compileOnly` and are not bundled into the wagon JAR or declared mandatory.
Development environments load all available compatibility mods by default.
Iris, Sodium and JEI are added only for client launch tasks.

Architectury is required by SwingThroughGrass. Fabric Traveler's Backpack also
requires Forge Config API Port. Forge Config API Port bundles its NightConfig
libraries; client integrations retain their own bundled libraries.

NeoForge includes Carry On, Traveler's Backpack (including sleeping bags),
Sophisticated Backpacks, JEI recipe displays, SwingThroughGrass, Lithium and
Iris shadow bridges. Fabric supports the corresponding Carry On, Traveler's
Backpack, JEI, SwingThroughGrass, Lithium and Iris integrations. Optional APIs
are loaded only when the matching mod is present.

## Unavailable integrations

No matching 26.2 artifacts were found for Sable, Patchouli, Touhou Little Maid,
Touhou Little Maid: Orihime, or the existing TaCZ 1.21.1 / Refabricated projects.
The current unofficial Fabric ports of Sophisticated Backpacks and Core also
have no 26.2 release. These projects are not added as dependencies.

The older Fabric SwingThrough project has no matching release. The
SwingThroughGrass project publishes both loader builds for 26.2 and is selected.
Cloth Config and Cardinal Components have 26.2 releases, but none of the selected
integrations requires them; they are not added just because older maid ports
used them. Patchouli handbook content will stay absent unless a matching
Patchouli release becomes available.

## Commands

```powershell
.\gradlew.bat :mc-26.2:neoforge:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.2:fabric:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.2:build
.\gradlew.bat :mc-26.2:neoforge:runClient
.\gradlew.bat :mc-26.2:fabric:runClient
.\gradlew.bat :mc-26.2:neoforge:runServer
.\gradlew.bat :mc-26.2:fabric:runServer
```

Launch tasks belong to each loader project, under `Tasks > minecraft`. The root
`build` includes all discovered targets; it does not expose game launch tasks.
Each loader produces its own JAR under `build/libs`, named
`tm_wagon-<loader>-26.2-1.0.1.jar`. Both loader outputs contain the full gameplay implementation.

Use `-PwithAllCompat=false` on NeoForge or `-PfabricWithAllCompat=false` on Fabric
for a minimal runtime. Individual `with<Name>` / `fabricWith<Name>` profiles
are `CarryOn`, `Backpacks`, `SwingThrough`, `Lithium`, `Jei` and `Iris`.
These flags affect development runtime only, leaving the compile-only APIs
available. Build output, run directories, local libraries and logs are ignored.

## Port verification

The loader-specific port smoke runs create fresh worlds under `build/`, exercise all
three wagon sizes and cargo ownership during assembly/restoration, test native
workstation menus and processing, repeatable moving sleep without respawn,
wood-sensitive recipes, shelving, copper chests, mule/camel driving and lead
refunds. The full profile also tests Carry On and the available backpack storage bridges.
Fabric checks ordinary block/item-use callbacks and custom recipe synchronization
with JEI, including wood and colour variants.
Client runs capture component and wagon previews and exit automatically.

```powershell
.\gradlew.bat :mc-26.2:neoforge:runClient -PportSmoke=true
.\gradlew.bat :mc-26.2:neoforge:runClient -PportSmoke=true -PwithAllCompat=false
.\gradlew.bat :mc-26.2:neoforge:runServer -PserverSmoke=true
.\gradlew.bat :mc-26.2:fabric:runClient -PportSmoke=true
.\gradlew.bat :mc-26.2:fabric:runClient -PportSmoke=true -PfabricWithAllCompat=false
.\gradlew.bat :mc-26.2:fabric:runServer -PportServerSmoke=true
```

The isolated server smoke requires an accepted EULA in `build/server-smoke`;
set its server properties for a local test before launch. Test helpers are not
included in the release JAR. Only `config/tm_wagon-server.toml` contains the
wagon server configuration, generated at startup and synchronized by the selected loader
(NeoForge or Forge Config API Port on Fabric).

## Primary references

- [Fabric's 26.2 development notes](https://fabricmc.net/2026/06/15/262.html).
- [NeoForge 26.2 MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle).
- [NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml).
- [NeoForm Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoform/maven-metadata.xml).
- [Fabric Loader metadata for 26.2](https://meta.fabricmc.net/v2/versions/loader/26.2).
- [Modrinth project-version API](https://docs.modrinth.com/api/operations/getprojectversions/).
- [Patchouli published files](https://www.curseforge.com/minecraft/mc-mods/patchouli/files/all).
- [Touhou Little Maid published files](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid/files/all).
