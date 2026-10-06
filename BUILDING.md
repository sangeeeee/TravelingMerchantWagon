# Building and maintaining the version targets

The repository uses one Gradle build with a shared core, Minecraft-version
modules, and loader-specific targets. The maintained target is NeoForge 1.21.1.
Fabric 1.21.1 is a scaffold and does not produce a mod JAR yet.

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
  are also stored here.
- `mc-1.21.1:neoforge` owns registrations, events, networking, configuration
  bindings, rendering integration, game tests and optional mod compatibility.
  Gameplay modules still coupled to those bindings remain here until the Fabric
  port introduces the relevant platform interfaces.
- `mc-1.21.1:fabric` reserves the future loader target. Its build deliberately
  produces no JAR and does not load NeoForge compatibility dependencies.

Common sources are exported as Gradle artifacts and compiled again by each
loader target against its own game environment. The shared core classes are
included directly in the loader JAR and bound to the same mod in development
runs. Players do not install separate core or common JARs.

## Commands

Use JDK 21 for this build. From the repository root on Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient -PclientSmoke=true
```

The last command uses an isolated client directory, renders the existing model
and animation previews, checks audio loading, and exits automatically. It does
not alter the regular development world's settings or saves.

To build only the loader target or use its other development runs:

```powershell
.\gradlew.bat :mc-1.21.1:neoforge:build
.\gradlew.bat :mc-1.21.1:neoforge:runMaidGameTestServer
```

The installable JAR is written to
`versions/mc-1.21.1/neoforge/build/libs/tm_wagon-neoforge-1.21.1-<mod-version>.jar`.
The root `build` aggregates every current project's build, including the Fabric
scaffold. It does not copy all target artifacts to a common output directory.

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
