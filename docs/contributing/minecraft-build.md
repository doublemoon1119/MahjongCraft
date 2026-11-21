# Minecraft Build

Build rules for the Minecraft modules: the cross-version common layer, version-common modules and loader
modules. General Gradle configuration, targets, examples and CI are in [Build](build.md).

## Java and Remapping

- Minecraft version-common and loader modules use the Java policy declared by their selected platform target
  in `gradle/platform-targets.toml`, not the core Java policy.
- The Minecraft loader convention makes every Fabric Loom remap task (`remapJar`, `remapSourcesJar`) share one
  build-wide slot, so remapping runs one task at a time while other work stays parallel. Two remap tasks running at
  once in the same daemon can fail with `ClosedFileSystemException` while reading a classpath JAR. New loader or
  version modules inherit this through the convention; do not add per-module workarounds.
- The root build script declares the Gradle plugins that Minecraft modules apply, such as Fabric Loom,
  without applying them, so every module loads the same plugin classes. Modules that apply different plugin
  sets would otherwise load Loom in separate class loaders, and Loom fails when it reaches another project, for
  example while an IDE sync creates `genSources` tasks. Declare a new loader's plugins there as well.

## Mod Dependency Requirements

- Keep dependency versions and runtime requirements in `gradle/libs.versions.toml`. A version used to
  compile the mod is not automatically its minimum supported version. Keep a separate requirement when
  metadata accepts older versions or a version range; do not derive it by trimming the build version.
- Each version/loader module selects the requirements its metadata needs through
  `MinecraftModPackagingExtension.dependencyRequirements`. Resource processing expands these values and
  tracks them as task inputs. Keep loader-specific keys and compatibility decisions in those modules,
  not in the shared packaging plugin.
- Reuse the exact target Minecraft version where appropriate. Keep intentional ranges, including `*`,
  unchanged unless support policy is explicitly being changed. Product and example metadata for the
  same target read the same catalog entries.

## Mod Versions

- A loader module's metadata version is its release train version with the Minecraft version and loader as
  SemVer build metadata, for example `0.4.0-SNAPSHOT+1.20.1-fabric`. Both come from the module's directory,
  as its JAR name `mahjongcraft-fabric-1.20.1-<version>.jar` does. Build metadata does not affect version
  ordering, so dependency checks compare only the release train version.
- A loader module's mod JAR is distributable unless its packaging sets `distributable` to `false`, as
  development-only modules and examples do. Distributable mods are what `collectCiArtifacts` gathers for CI:
  the remapped JAR for Fabric Loom modules, otherwise the plain JAR.
- `-PmahjongcraftBuildMetadata=<value>` appends `.<value>` to the metadata version and `+<value>` to the JAR
  version, for example to identify a CI build by its commit. It must be dot-separated letters, digits and
  hyphens, and it leaves project versions and the version policy unchanged.

## Development Runtime Mods

- Minecraft loader modules can declare named groups of mods that development runs load only on request,
  such as a shader mod for compatibility checks. Pass the groups as a comma-separated list:

  ```shell
  ./gradlew :minecraft_v1.20.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" "-PmahjongcraftDevMods=iris,modmenu"
  ```

- Without the switch, or with an empty value, no group is loaded and development runs stay unchanged. A
  group a module does not declare fails the build and lists the groups it does declare. Like the example
  switch, it is not saved in `local.dev.properties`.
- Each loader module declares its groups in its build script through the `devMods` extension of the
  Minecraft loader convention, for example `group("iris", libs.sodium1201, libs.iris1201)`. Versions live in
  `gradle/libs.versions.toml`. Fabric Loom modules add selected groups to `modLocalRuntime`, so the mods
  are neither bundled nor published as dependencies; other loaders set `configurationName` to their
  equivalent.
- Groups hold runtime mods only. Product code must not depend on them. An integration that compiles
  against another mod's API, such as a ModMenu settings entry point, declares that API as compile-only and
  keeps the other mod optional in its metadata; the group only supplies the mod for development runs.
- Fabric Loom removes the libraries a mod bundles in its own JAR when it prepares the mod for development
  runs, and expects them as Maven dependencies instead. When a mod's published metadata does not declare
  them, list those libraries in the group as well, at the versions the mod bundles.
- Development runs use the libraries of the target's Minecraft release. Loom would otherwise choose them for
  the Java version running Gradle, for example upgrading LWJGL on Java 19 or newer, while the runs use the
  target's Java toolchain. The loader convention sets Loom's runtime Java version to the target's toolchain
  unless a build sets it explicitly.
- A group name means the same kind of environment on every target, but each target chooses the actual mods.
  The groups a target declares, and how to use them, belong in that loader module's README.
