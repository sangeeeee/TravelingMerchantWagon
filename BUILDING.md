# Building and maintaining the version targets

The repository uses one Gradle build with a shared core, Minecraft-version
modules, and loader-specific targets. The maintained targets are NeoForge 1.21.1 and Fabric 1.21.1.
Both targets produce independently installable mod JARs.

```text
core/
versions/mc-1.21.1/
  gradle.properties
  common/
  neoforge/
  fabric/
shared-assets/                 ignored, local editable master assets
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
- `mc-1.21.1:fabric` compiles common sources through Fabric Loom using official
  Mojang mappings. It owns its Fabric compatibility APIs and development runtime
  profiles, registrations, network payloads, events, configuration bindings and
  native Fabric compatibility adapters.

Common sources are exported as Gradle artifacts and compiled again by each
loader target against its own game environment. The shared core classes are
included directly in the loader JAR and bound to the same mod in development
runs. Players do not install separate core or common JARs.

## Commands

Use JDK 21 for this build. From the repository root on Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat :mc-1.21.1:fabric:runClient
.\gradlew.bat :mc-1.21.1:neoforge:runClient
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
build scripts. From an aggregate directory, launch commands must specify the
version and loader path. The Fabric client uses
`versions/mc-1.21.1/fabric/run`; NeoForge uses its own run directory.

Installable JARs are written to each loader target:

- `versions/mc-1.21.1/neoforge/build/libs/tm_wagon-neoforge-1.21.1-<mod-version>.jar`
- `versions/mc-1.21.1/fabric/build/libs/tm_wagon-fabric-1.21.1-<mod-version>.jar`

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

Root `gradle.properties` contains shared identity and build settings only.
`versions/mc-1.21.1/gradle.properties` defines this Minecraft version and mappings.
Each loader's own properties and build script define its compatibility versions
and dependencies. `gradle/load-target-properties.gradle` explicitly loads those
target settings; another Minecraft target does not inherit them.

Optional APIs remain compile-only in the NeoForge target. Development runtime
flags such as `withSable`, `withBackpacks`, and `withCarryOn`, and existing local
test-JAR properties, are still available on that target. Local JAR paths should
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
