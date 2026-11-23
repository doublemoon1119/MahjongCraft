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
- The configuration cache is enabled by default. Custom tasks must not read `Task.project` while executing;
  pass the data they need as task properties at configuration time. A new platform target must build and run
  its `run` tasks with the configuration cache, or document why its builds pass `--no-configuration-cache`.
- The local build cache is enabled by default and lives in the Gradle user home, so worktrees and branches
  reuse each other's compile, test and check results. A task is cached only when every input that affects it
  is declared: a test that reads a file or setting outside its declared inputs must declare it, as tests do for
  the `MAHJONGCRAFT_*` environment variables. Custom tasks are not cacheable unless marked `@CacheableTask`.
  Pass `--no-build-cache` or `--rerun` to run a task again regardless.
- The built-in `core` target loads only logic, flow, AI, extension API, and testing modules. Formal
  platform targets are declared in `gradle/platform/<platform>/<version>/targets.toml`; directory presence
  alone does not
  make a platform releasable.
- Target selection precedence is `-PmahjongcraftTarget`,
  `ORG_GRADLE_PROJECT_mahjongcraftTarget`, `MAHJONGCRAFT_TARGET`, `local.dev.properties`, then
  `core`. On PowerShell, quote target properties containing dots, for example:

  ```shell
  .\gradlew.bat build "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
  ```

- A selection may list several targets separated by commas, for example
  `minecraft-v1.20.1-fabric,minecraft-v1.20.1-forge`, to build and sync both loaders of one Minecraft
  version together. The build loads the union of their modules once. Each module takes its Java version and
  the libraries its runtime provides from the targets that contain it; a module shared by targets with
  different Java versions fails the selection, so select those targets separately. `core` cannot be
  combined with other targets. CI still verifies every target on its own.
- Properties that only apply to Minecraft builds, such as extra mods for development runs and build
  metadata, are described in [Minecraft Build](minecraft-build.md).
- Core modules use `core-java-toolchain` and `core-java-release` from `gradle/libs.versions.toml`.
  Platform modules use the Java policy declared by their selected platform target; see
  [Minecraft Build](minecraft-build.md) for the Minecraft modules.
- MahjongCraft release trains are independent: Minecraft modules, logic, flow, AI, and extension
  API each read their own version from `gradle/libs.versions.toml`. The root and testing projects remain
  `0.0.0-dev` because they are not published.

## Version Catalogs and Targets

- `gradle/libs.versions.toml` (`libs`) holds the versions shared by every platform version: Kotlin, the
  core libraries and build tooling.
- Each platform version has its own folder, `gradle/platform/<platform>/<version>/`:
  - `targets.toml` declares that version's platform targets. The build reads every such file and fails
    when two files declare the same target ID.
  - `versions.toml`, when present, holds that version's dependencies and runtime requirements as a separate
    catalog. Each platform's build guide names its catalogs.
- Catalog names use lowercase words joined by `-`, never camelCase, so Gradle accessors stay predictable,
  and no segment may be a Kotlin keyword such as `for`. A name must not also be the prefix of another
  name, for example `foo` next to `foo-bar`; choose a distinct name instead.
- Group entries into sections with a `#` comment heading, in the same order in `[versions]` and
  `[libraries]`. Keep runtime requirements written into package metadata apart from the compile versions,
  named `require-<loader>-<dependency>`.

## Runtime-Provided Versions

- Some platforms load libraries the build output does not bundle, for example a loader that ships the Kotlin
  standard library. A target records those versions in `targets.toml` under `[targets.provided-versions]`,
  keyed by `libs` version names. `kotlin` stands for the Kotlin standard library.
- Only one copy of a provided library is loaded, so the project may not use a newer version than any target
  provides:
  - `kotlin-api-version` in `libs` caps the standard library API that Kotlin code may use, and is applied to
    every Kotlin compilation. It must not exceed the oldest provided `kotlin`. The compiler, `kotlin`, may be
    newer.
  - Every other provided name must be a `libs` version no newer than the provided one.
- `verifyProvidedVersions`, part of the root `check`, reads every target, whichever target is selected, and
  fails when a version exceeds what a target provides, when a provided name is not a `libs` version, or when
  two versions cannot be compared because one is not purely numeric.
- To upgrade Kotlin or a provided library:
  1. Wait until every target's runtime provides the new version, and update `provided-versions` and the
     runtime requirement in that target's metadata.
  2. Raise the version in `libs`, and `kotlin-api-version` for a new Kotlin minor version.
  3. Run the full verification for every target.

## Example Builds

- A target may declare `example-modules` in its `targets.toml` entry. They are loaded only when
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

## Continuous Integration

- `.github/workflows/ci.yml` runs on every push and pull request, whatever the branch, and can be started
  manually. Branches that predate the workflow do not contain it, so pushing them does not run CI. A newer
  push to the same branch or pull request cancels the run in progress.
- The first job runs `./gradlew writeCiTargetMatrix`, which writes `build/ci/target-matrix.json`: the
  built-in `core` target and every target in the `targets.toml` files, each with its Java toolchain
  and whether it has example modules. A new target is verified without editing the workflow.
- Each target then runs the same full verification as a local check, in its own job:

  ```shell
  ./gradlew build detektAll --continue --stacktrace "-PmahjongcraftTarget=<target>" "-PmahjongcraftExample=<true|false>"
  ```

  CI sets the `CI` environment variable, so modules that start Koin recheck their whole dependency graph
  on every build, as `-PmahjongcraftKoinStrictSafety=true` does locally.
  Gradle runs on JDK 25. The target's toolchain JDK is installed separately and found through
  `org.gradle.java.installations.fromEnv`; toolchain downloads are disabled, so a missing JDK fails the job.
- Pushes save the Gradle cache, including Loom's Minecraft files; pull requests only read it.
- CI builds pass the short commit hash as [build metadata](minecraft-build.md#mod-versions), so a Minecraft mod built
  by CI reads like `0.4.0-SNAPSHOT+1.20.1-fabric.a0f6307` and its JAR like
  `mahjongcraft-fabric-1.20.1-0.4.0-SNAPSHOT+a0f6307.jar`. Local builds leave it out.
- A failed job uploads its test and detekt reports. A passing job runs `collectCiArtifacts`, which gathers
  the target's distributable files into `build/ci/artifacts/`, and uploads them; a target without
  distributable files, such as `core`, uploads nothing. Each platform's build convention decides what is
  distributable. Both uploads are kept for seven days and are not releases.
