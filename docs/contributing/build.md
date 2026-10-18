# Build

## Gradle Build Configuration

- The repository tracks `gradle.properties` with conservative daemon defaults suitable for the
  multi-module build. They are maximum JVM limits rather than memory reserved at startup.
- Developers may override `org.gradle.jvmargs` in the Gradle user home
  (`~/.gradle/gradle.properties`). CI jobs should set limits appropriate for their runner instead of
  assuming the repository defaults fit every environment.
- Parallel project execution is enabled by default. Memory-constrained environments can pass
  `--no-parallel` and lower `org.gradle.workers.max` in their Gradle user properties.
- The configuration cache is enabled by default. Custom tasks must not read `Task.project` while executing;
  pass the data they need as task properties at configuration time. A new platform target must build and run
  its `run` tasks with the configuration cache, or document why its builds pass `--no-configuration-cache`.
- The built-in `core` target loads only logic, flow, AI, extension API, and testing modules. Formal
  platform targets are declared in `gradle/platform-targets.toml`; directory presence alone does not
  make a platform releasable.
- Target selection precedence is `-PmahjongcraftTarget`,
  `ORG_GRADLE_PROJECT_mahjongcraftTarget`, `MAHJONGCRAFT_TARGET`, `local.dev.properties`, then
  `core`. On PowerShell, quote target properties containing dots, for example:

  ```powershell
  .\gradlew.bat build "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
  ```

- Core modules use `core-java-toolchain` and `core-java-release` from `gradle/libs.versions.toml`.
  Minecraft version-common and loader modules use the Java policy declared by their selected
  platform target.
- MahjongCraft release trains are independent: Minecraft modules, logic, flow, AI, and extension
  API each read their own version from `gradle/libs.versions.toml`. The root and testing projects remain
  `0.0.0-dev` because they are not published.

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

## Example Builds

- A target may declare `example-modules` in the existing platform catalog. They are loaded only when
  `-PmahjongcraftExample=true` is passed. Missing or `false` keeps them out of the project graph;
  other values are rejected. The switch is not saved in `local.dev.properties`.
- Example modules use the existing conventions and read their versions from the version catalog.
  The `:example` parent and its platform-independent children follow the extension API version.
  Minecraft example modules follow the Minecraft mod version.
- Product modules never depend on examples. Example resources and classes belong only to the example
  mod. Its development runs use separate worlds and do not modify the product's run classpath.
- Keep metadata defaults unchanged for product modules. Examples set their own identity through
  `MinecraftModPackagingExtension` instead of changing root metadata or copying compiler settings.
- Verify both example-enabled and default builds. Test the actual remapped JAR contents, not just
  compilation, and keep configuration cache enabled. See the
  [Minecraft example guide](../../platform/minecraft/example/README.md) for run commands.
