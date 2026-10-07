# Building and maintaining the version targets

The repository uses one Gradle build with a shared core, Minecraft-version
modules, and loader-specific targets. The gameplay targets are NeoForge 1.21.1,
Fabric 1.21.1 and Forge 1.20.1. NeoForge and Fabric 26.3 have port environments
ready, with gameplay implementation pending.
Each loader target produces its own mod JAR.
See the [Forge target notes](versions/mc-1.20.1/forge/README.md) for its dependencies
and development profiles.

```text
core/
versions/mc-1.21.1/
  gradle.properties
  common/
  neoforge/
  fabric/
shared-assets/                 ignored, local editable master assets
versions/mc-1.20.1/
  gradle.properties
  common/
  forge/
versions/mc-26.3/
  gradle.properties
  compatibility.gradle
  common/
  neoforge/
  fabric/
```

## Module boundaries

- `core` contains Java-only part dimensions, assembly-jack motion, driving
  parameters, acceleration/braking dynamics, and fabric colour processing. It
  compiles with Java 17 bytecode and has no Minecraft, loader or optional mod API
  dependencies.
- `mc-1.21.1:common` contains the Minecraft-facing placement geometry, oriented
  collision boxes, pose transforms, wood registry helpers, and dye palette. Its
  independent build compiles against vanilla Minecraft through NeoForm, so
  accidental NeoForge API references fail compilation. Runtime assets and data
  are also stored here. `src/gameplay/java` holds shared gameplay and rendering
  sources that require platform bindings or optional APIs. Both loaders compile
  these sources against their own dependencies; they are excluded from the
  standalone vanilla-only common compilation.
- `mc-1.21.1:neoforge` owns registrations, events, networking, configuration
  bindings, rendering integration, game tests and optional mod compatibility.
  Loader-independent gameplay is exported by the common module.
- `mc-1.20.1:forge` uses the Legacy Forge toolchain and its own common/gameplay
  sources, NBT and recipe formats, events, networking, optional mod APIs and Mixins.
  It excludes Sable; its client-only shader dependencies do not enter server runs.
- `mc-1.21.1:fabric` compiles common sources through Fabric Loom using official
  Mojang mappings. It owns its Fabric compatibility APIs and development runtime
  profiles, registrations, network payloads, events, configuration bindings and
  native Fabric compatibility adapters.

Common sources are exported as Gradle artifacts and compiled again by each
loader target against its own game environment. The shared core classes are
included directly in the loader JAR and bound to the same mod in development
runs. Players do not install separate core or common JARs.

## Commands

Use JDK 21 or 25 to launch Gradle, with the target-specific JDKs available as
toolchains. From the repository root on Windows:
The Forge 1.20.1 target emits Java 17 bytecode and uses Java 17 to run Minecraft.

```powershell
.\gradlew.bat build
.\gradlew.bat :mc-1.21.1:fabric:runClient
.\gradlew.bat :mc-1.21.1:neoforge:runClient
.\gradlew.bat :mc-1.20.1:forge:runClient
.\gradlew.bat :mc-1.20.1:forge:runGameTestServer
.\gradlew.bat :mc-1.20.1:forge:runPortClientSmoke
.\gradlew.bat :mc-1.21.1:fabric:runGameTest
.\gradlew.bat :mc-1.21.1:neoforge:runGameTestServer
.\gradlew.bat :mc-1.21.1:neoforge:runClient -PclientSmoke=true
```

The last command uses an isolated NeoForge client directory, renders the existing model
and animation previews, checks audio loading, and exits automatically. It does
not alter the regular development world's settings or saves.

To build only the loader target or use its other development runs:

```powershell
.\gradlew.bat :mc-1.21.1:neoforge:build
.\gradlew.bat :mc-1.21.1:neoforge:runMaidGameTestServer
.\gradlew.bat :mc-1.21.1:build
```

In IntelliJ's Gradle tool window, select `mc-1.21.1 > fabric > Tasks > minecraft >
runClient` or the corresponding `neoforge` loader. Launch and test tasks belong
only to loader projects. The root and version parents expose aggregate build
tasks, with no game-launch aliases. Reload the Gradle project after changing
build scripts. An IDE request scoped to a loader project can use `runClient`
or `runServer`; the build checks the request's project path as well as its working
directory. Saved IDE configurations targeting the repository root must contain
the full task path, such as `:mc-1.20.1:forge:runClient`. Selecting a loader in the
task tree does not change an existing saved configuration's target. Use the
versioned `Minecraft 1.20.1 Forge Client` run configuration or edit its Tasks field.
For older IDE configurations containing only `runClient` or `runServer`, an
ignored workstation file `gradle-local.properties` can specify
`runTarget=:mc-1.20.1:forge`. The build then resolves that request to exactly one
loader and prints the chosen task; it does not add launch tasks to the root.
`-PrunTarget=:mc-1.21.1:fabric` overrides this preference. Fully qualified tasks
and requests scoped to a loader always keep their selected target. Without this
preference, aggregate-directory launches require the version and loader path.
The Fabric client uses
`versions/mc-1.21.1/fabric/run`; NeoForge uses its own run directory.

Installable JARs are written to each loader target:

- `versions/mc-1.21.1/neoforge/build/libs/tm_wagon-neoforge-1.21.1-<mod-version>.jar`
- `versions/mc-1.21.1/fabric/build/libs/tm_wagon-fabric-1.21.1-<mod-version>.jar`
- `versions/mc-1.20.1/forge/build/libs/tm_wagon-forge-1.20.1-<mod-version>.jar`

Settings automatically discover `versions/mc-*` directories and their `common`,
`fabric`, `neoforge`, and `forge` modules that have a Gradle build script. The
root `build` aggregates the core and every discovered version; each version's
`build` aggregates its own modules. New targets require no root launcher or
build-list edits. It does not copy
target artifacts to a common directory. Fabric verification includes its game
tests; test classes and preview tools are excluded from release JARs.

For Fabric development:

```powershell
.\gradlew.bat :mc-1.21.1:fabric:build
.\gradlew.bat :mc-1.21.1:fabric:runClient
.\gradlew.bat :mc-1.21.1:fabric:runGameTest -PfabricWithAllCompat=true
.\gradlew.bat :mc-1.21.1:fabric:runClient -PfabricSmoke=true -PfabricWithAllCompat=true
```

Fabric preview sources compile in a separate `clientSmoke` source set and never
enter release JARs. `:mc-1.21.1:fabric:runClientSmokeTest` uses `fabric/build/client-smoke`;
the `fabricSmoke` property on `runClient` uses the normal Fabric run directory.
Preview screenshots are saved in the selected run directory.
See the [Fabric target notes](versions/mc-1.21.1/fabric/README.md)
for required libraries and individual compatibility profiles.

## Dependencies and properties

### Minecraft 26.3 port environment

`versions/mc-26.3` adds independent `common`, `neoforge` and `fabric` modules.
Only their dependency environment and loader entry points are currently ready;
wagon gameplay still needs porting. See the [26.3 dependency inventory and port
notes](versions/mc-26.3/README.md) for available and omitted integrations.

```powershell
.\gradlew.bat :mc-26.3:neoforge:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.3:fabric:verifyCompatibilityDependencies
.\gradlew.bat :mc-26.3:build
.\gradlew.bat :mc-26.3:neoforge:runClient
.\gradlew.bat :mc-26.3:fabric:runClient
```

26.3 requires Java 25; 1.21.1 remains on Java 21 and Forge 1.20.1 retains its
existing Java settings. To use local JDKs without committing machine-specific
paths, add this to the ignored `gradle-local.properties`:

```properties
org.gradle.java.installations.paths=D:/dev/java/jdk17,D:/dev/java/jdk21,D:/dev/java/jdk25
```

Gradle discovers these installations and selects the version declared by each
module. Command-line `-Porg.gradle.java.installations.paths=...` overrides the
local setting. The root `build` also includes the new target; launch tasks
continue to belong to individual loader modules.

### Existing targets

Root `gradle.properties` contains shared identity and build settings only.
`versions/mc-1.21.1/gradle.properties` defines this Minecraft version and mappings.
Each loader's own properties and build script define its compatibility versions
and dependencies. `gradle/load-target-properties.gradle` explicitly loads those
target settings; another Minecraft target does not inherit them.

Compatibility APIs remain compile-only; each loader's development runs load all
integrations and their required libraries by default. Normal `runClient` needs
no extra arguments. The NeoForge runtime includes Touhou Little Maid, Carry On,
both backpacks and Sophisticated Core, Sable, Patchouli, TACZ, JEI, Lithium,
SwingThroughGrass and Architectury, and Iris/Sodium. Each target pins its own
versions. The Iris 1.8.14-beta.1 / Sodium 0.8.13 pair allows graphics
integration and Sable to coexist.

Use `-PwithAllCompat=false` (NeoForge) or `-PfabricWithAllCompat=false` (Fabric)
to select a minimal runtime. Individual `with<Name>` / `fabricWith<Name>` flags
override the default; for example `-PwithIris=false` disables Iris/Sodium only.
The available profile names are `Maid`, `Backpacks`, `CarryOn`, `Sable`,
`Patchouli`, `Guns`, `SwingThrough`, `Lithium`, `Iris`, and `Jei`.
Existing NeoForge local test-JAR properties replace the corresponding downloaded
mod, avoiding duplicates. Local JAR paths should
be absolute, and local dependency files belong in the target's ignored `libs/`.
The shared Gradle download cache is safe to reuse: target classpaths remain
separate.

The Fabric dependency inventory, unofficial port choices and target-specific
runtime flags are documented in [its README](versions/mc-1.21.1/fabric/README.md).
Validate that environment separately with:

```powershell
.\gradlew.bat :mc-1.21.1:fabric:verifyCompatibilityDependencies
.\gradlew.bat :mc-1.21.1:fabric:verifyCompatibilityDependencies -PfabricWithAllCompat=true
```

## Resources and Git

Runtime textures, models, sounds, translations, recipes, geometry data and
handbook pages in `common/src/main/resources` are tracked. A clean checkout can
build without editable master assets or previously generated build output.
NeoForge metadata templates and its Mixin configuration live in the loader
project.

Blockbench master projects have moved to `shared-assets/modeling`. This directory
is ignored, along with each target's build output, local dependencies, run
directories, screenshots, logs, crash reports, and generator caches. Existing
export and validation tools point at the new locations. Export tools need the
local master assets; normal builds and resource validation do not.

```powershell
python tools/validate_handbook.py
python tools/validate_localizations.py
```

For future game versions, reuse Java-only core rules where suitable, compile
Minecraft-dependent shared source against that version, and add resource or API
adaptations in its own modules. Keep optional compatibility libraries and their
Mixin implementations scoped to targets that actually support them.
