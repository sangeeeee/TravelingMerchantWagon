# Forge 1.20.1 target

This target ports the wagon gameplay, rendering, recipes and optional integrations
to Forge 1.20.1. It shares the Java-only core with the other targets and owns its
Minecraft 1.20.1 sources, data formats, networking and compatibility adapters.

## Dependencies

Versions were checked against publisher release metadata and the archives'
`META-INF/mods.toml` declarations. Each version pin belongs to this target's
`gradle.properties`; nothing is inherited from the 1.21.1 loader projects.

| Dependency | Forge 1.20.1 version | Purpose / source |
| --- | --- | --- |
| Minecraft Forge | 47.4.10 | [Recommended Forge release](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) |
| GeckoLib | 4.8.4 | Required animation library; [release](https://modrinth.com/mod/geckolib/version/aC5KMoNg) |
| Touhou Little Maid | 1.5.3 | Optional maid API; [release](https://modrinth.com/mod/touhou-little-maid/version/g1SKoGQJ) |
| Carry On | 2.1.2.7 | Optional carrying API; [release](https://modrinth.com/mod/carry-on/version/edGQD16r) |
| Traveler's Backpack | 9.1.57 | Optional backpack API; [release](https://modrinth.com/mod/travelersbackpack/version/wuJ2J0SL) |
| Sophisticated Backpacks | 3.26.7.2188 | Optional backpack API; [release](https://modrinth.com/mod/sophisticated-backpacks/version/2bWtsB6d) |
| Sophisticated Core | 1.5.5.2373 | Required by Sophisticated Backpacks; [release](https://modrinth.com/mod/sophisticated-core/version/g4iv6aQm) |
| Patchouli | 85 | Optional guidebook API; [release](https://modrinth.com/mod/patchouli/version/94dtOLgZ) |
| Timeless and Classics Zero | 1.1.8-hotfix2 | Optional original Forge gun API; [release](https://modrinth.com/mod/timeless-and-classics-zero/version/AzCBJlex) |
| Clean Swing Through Grass | 1.8 | Optional grass targeting API; [file 5962530](https://www.curseforge.com/minecraft/mc-mods/clean-swing-through-grass/files/5962530) |
| JEI | 15.62.0.219 | Optional recipe viewer API and development runtime; [release](https://modrinth.com/mod/jei/version/uyTkeINn) |
| MezzConfig | 0.6.8 | Required by this JEI version; [release](https://modrinth.com/mod/mezzconfig/version/EAG7rQQs) |
| Oculus | 1.8.0 | Forge shader test environment; [release](https://modrinth.com/mod/oculus/version/iQ1SwGc3) |
| Embeddium | 0.3.31 | Oculus renderer; [release](https://modrinth.com/mod/embeddium/version/UTbfe5d1) |

GeckoLib is the sole mandatory third-party player dependency. Other APIs are
compile-only, with optional development runtimes; they are not embedded into the
wagon JAR. The dependency remapper is deliberately non-transitive, so required
mods such as Sophisticated Core and MezzConfig are listed explicitly.

Carry On uses the 2.1.x series on this game version. The previous 1.21.1 minimum
of 2.2.4 cannot be reused here; this target uses a separate adapter for the 2.1.x API.
The original Forge TACZ release replaces the 1.21.1-specific TACZ ports.
Oculus/Embeddium supply the shader development environment. The optional Oculus
shadow hook includes the wagon assembly models in block-entity shadow rendering.

Sable is deliberately excluded, including its helper libraries. The 1.20.1 port
uses the wagon's own collision system and contains no Sable integration code.
Official Lithium has no Forge 1.20.1 release and is also excluded. No substitute
physics or optimization mod is introduced automatically.

## Development

Run Gradle itself with JDK 21, as required by the other version targets. This
target emits **Java 17 bytecode** and runs Minecraft with a **Java 17 toolchain**.
ModDevGradle's Legacy Forge plugin remaps dependency archives to Mojang mappings
and reobfuscates the output JAR to Forge's SRG mappings.

From the repository root:

```powershell
.\gradlew.bat :mc-1.20.1:forge:build
.\gradlew.bat :mc-1.20.1:forge:verifyCompatibilityDependencies
.\gradlew.bat :mc-1.20.1:forge:runClient
.\gradlew.bat :mc-1.20.1:forge:runServer
.\gradlew.bat :mc-1.20.1:forge:runGameTestServer
.\gradlew.bat :mc-1.20.1:forge:runPortClientSmoke
```

The Gradle IDE launcher is `mc-1.20.1 > forge > Tasks > minecraft > runClient`
or `runServer`. Root `build` includes this target automatically. The client uses
`forge/run`, the server uses `forge/run-server`, and artifacts go to
`forge/build/libs/tm_wagon-forge-1.20.1-<mod-version>.jar`.

All optional development profiles are enabled by default. To start with only
required dependencies, pass `-PforgeWithAllCompat=false`. Individual profiles
override that default:

```powershell
.\gradlew.bat :mc-1.20.1:forge:runClient -PforgeWithAllCompat=false -PforgeWithMaid=true
.\gradlew.bat :mc-1.20.1:forge:runClient -PforgeWithShaders=false
```

Available profile suffixes: `Maid`, `CarryOn`, `Backpacks`, `Patchouli`, `Guns`,
`SwingThrough`, `Jei`, `Shaders`. These properties affect only this Forge target.
Dependency resolution checks verify API availability, not gameplay integration.

The 1.20.1 common module contains its own geometry, shared gameplay and runtime
resources. Native item NBT, recipe serializers, Forge events, SimpleChannel
networking and GeckoLib 4 rendering replace the corresponding 1.21.1 APIs.

The server GameTests cover assembly conversion, capacities and material variants,
container ownership, offline block-entity NBT loading, rotated collision, seating,
both backpack inventory round trips, recipe/task registration, moving mat sleep
and hitched-horse driving.
The client smoke run creates an isolated flat world, verifies player mat sleep
through a real connection (movement, wake and unchanged respawn), renders items and three sizes
of block/entity wagons with optional equipment, saves a screenshot and exits.
Client-only Oculus/Embeddium are excluded from dedicated-server classpaths.
Test and preview sources are never included in the release JAR.
