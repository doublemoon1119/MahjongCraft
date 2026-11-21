# Build

## Gradle Build Configuration

- The repository tracks `gradle.properties` with conservative daemon defaults suitable for the
  multi-module build. They are maximum JVM limits rather than memory reserved at startup.
- The Kotlin compile daemon has its own limit, `kotlin.daemon.jvmargs`, set higher than the Gradle daemon
  because compiling the Minecraft modules needs more heap.
- Developers may override `org.gradle.jvmargs` and `kotlin.daemon.jvmargs` in the Gradle user home
  (`~/.gradle/gradle.properties`). CI jobs should set limits appropriate for their runner instead of
  assuming the repository defaults fit every environment.
- Parallel project execution is enabled by default. Memory-constrained environments can pass
  `--no-parallel` and lower `org.gradle.workers.max` in their Gradle user properties.
- The Minecraft loader convention makes every Fabric Loom remap task (`remapJar`, `remapSourcesJar`) share one
  build-wide slot, so remapping runs one task at a time while other work stays parallel. Two remap tasks running at
  once in the same daemon can fail with `ClosedFileSystemException` while reading a classpath JAR. New loader or
  version modules inherit this through the convention; do not add per-module workarounds.
- The configuration cache is enabled by default. Custom tasks must not read `Task.project` while executing;
  pass the data they need as task properties at configuration time. A new platform target must build and run
  its `run` tasks with the configuration cache, or document why its builds pass `--no-configuration-cache`.
- The built-in `core` target loads only logic, flow, AI, extension API, and testing modules. Formal
  platform targets are declared in `gradle/platform-targets.toml`; directory presence alone does not
  make a platform releasable.
- Target selection precedence is `-PmahjongcraftTarget`,
  `ORG_GRADLE_PROJECT_mahjongcraftTarget`, `MAHJONGCRAFT_TARGET`, `local.dev.properties`, then
  `core`. On PowerShell, quote target properties containing dots, for example:

  ```shell
  .\gradlew.bat build "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
  ```

- Core modules use `core-java-toolchain` and `core-java-release` from `gradle/libs.versions.toml`.
  Minecraft version-common and loader modules use the Java policy declared by their selected
  platform target.
- MahjongCraft release trains are independent: Minecraft modules, logic, flow, AI, and extension
  API each read their own version from `gradle/libs.versions.toml`. The root and testing projects remain
  `0.0.0-dev` because they are not published.

## Minecraft Mod Dependency Requirements

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

## Example Builds

- A target may declare `example-modules` in the existing platform catalog. They are loaded only when
  `-PmahjongcraftExample=true` is passed. Missing or `false` keeps them out of the project graph;
  other values are rejected. The switch is not saved in `local.dev.properties`.
- Example modules use the existing conventions and read their versions from the version catalog.
  The `:example` parent and its platform-independent children follow the extension API version.
  Minecraft example modules follow the Minecraft mod version.
- All example code lives under `example/`. Platform-independent libraries are immediate children;
  platform adapters live under `example/platform/<platform>/`. Their Gradle paths are
  `:example:example-<library>` and `:example-<platform>_<module-path>`, respectively.
  For example, `example/platform/minecraft/v1.20.1/fabric` maps to
  `:example-minecraft_v1.20.1_fabric`. Loader projects stay at the same Gradle level as product
  loaders; for Loom-based targets, this lets them share plugin classes. The prefix keeps example
  artifact names distinct from product artifacts. Product project paths remain unchanged.
- Product modules never depend on examples. Example resources and classes belong only to the example
  mod. Its development runs use separate worlds and do not modify the product's run classpath.
- Keep metadata defaults unchanged for product modules. Examples set their own identity through the
  owning packaging convention instead of changing root metadata or copying compiler settings. For
  example, Minecraft adapters use `MinecraftModPackagingExtension`.
- Verify both example-enabled and default builds. Test the actual remapped JAR contents, not just
  compilation, and keep configuration cache enabled. See the
  [Minecraft example guide](../../example/platform/minecraft/README.md) for run commands.
- Keep example documentation actionable: link to the public registration entry point and the focused
  tests, state which checks are automated or manual, and do not describe a missing-mod world test as
  complete until the packaged-instance recovery steps have actually been run.
- In shared guides, present version/loader-specific commands and paths as examples, and name the
  adapter actually verified. Do not imply that checking one adapter verifies other versions or loaders.
  An adapter's own guide can describe its implementation directly.
