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
  version together, or targets of different Minecraft versions. The build loads the union of their modules
  once. Each module takes its Java version and the libraries its runtime provides from the targets that
  contain it; a module shared by targets with different Java versions compiles with the newest of their
  toolchains to the oldest of their releases, so every target can load it. `core` cannot be combined with
  other targets. CI still verifies every target on its own.
- Properties that only apply to Minecraft builds, such as extra mods for development runs and build
  metadata, are described in [Minecraft Build](minecraft-build.md).
- Core modules use `core-java-toolchain` and `core-java-release` from `gradle/libs.versions.toml`.
  Platform modules use the Java policy declared by their selected platform target; see
  [Minecraft Build](minecraft-build.md) for the Minecraft modules.
- Every Kotlin and Java compilation treats warnings as errors, in every module and in `build-logic`. A
  dependency or compiler upgrade that introduces warnings has to resolve them in the same change. Add
  `-Xrender-internal-diagnostic-names` to a module's Kotlin compiler arguments temporarily to print the
  name a `@Suppress` needs.
- MahjongCraft release trains are independent: Minecraft modules, logic, flow, AI, and extension
  API each read their own version from `gradle/libs.versions.toml`. Modules under `platform/minecraft`
  and `example/platform/minecraft` get `minecraft-mod-version` from the base convention; the others set
  their version in their build script. The root and testing projects remain `0.0.0-dev` because they are
  not published.

## Version Catalogs and Targets

- `gradle/libs.versions.toml` (`libs`) holds the versions shared by every platform version: Kotlin, the
  core libraries and build tooling.
- Each platform version has its own folder, `gradle/platform/<platform>/<version>/`:
  - `targets.toml` declares that version's platform targets. The build reads every such file and fails
    when two files declare the same target ID. A target whose build tools need a JDK other than its
    `java-toolchain` lists it in `additional-java-toolchains`; CI installs only the JDKs a target declares.
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
- Keep example documentation actionable: describe what the example does and how to use it, and link to
  the public registration entry point and the focused tests.
- Documentation describes behavior and usage for its readers. It does not record verification
  progress, such as which checks passed, are pending or were run on which adapter.
- In shared guides, present version/loader-specific commands and paths as examples. An adapter's own
  guide can describe its implementation directly.

## Test Coverage

- `-PmahjongcraftCoverage=true` measures test coverage with [Kover](https://github.com/Kotlin/kotlinx-kover). Without
  it Kover is not applied at all: it attaches an agent to every test task, which makes tests about a quarter slower.
- With the switch, every Kotlin module applies Kover except the test support modules under `testing/`, and the root
  merges them. The root report covers the selected targets:

  ```shell
  ./gradlew :koverHtmlReport :koverXmlReport "-PmahjongcraftCoverage=true" "-PmahjongcraftTarget=<target>"
  ```

  The reports are written to `build/reports/kover/html/index.html` and `build/reports/kover/report.xml`. Use the
  root task paths: a bare `koverHtmlReport` also writes a report for every module.
- Coverage is measured on the JVM, the only target of the core modules. Debug and example modules are included;
  the HTML report lists them under their own packages.
- When targets of two Minecraft versions are built together, a class compiled from the shared source by both
  version modules appears once, covered if either version's tests ran it. Build one target to see a single
  version.
- There is no coverage threshold.

## Continuous Integration

- CI is split by platform: `.github/workflows/ci-core.yml` verifies the built-in `core` target, and each
  platform has its own `ci-<platform>.yml`, such as `ci-minecraft.yml`. A platform's workflow may name its
  platform, pass that platform's build properties and add steps only that platform needs. A platform can add
  further workflows for checks kept apart from the verification, such as `ci-minecraft-client.yml`. A new
  platform adds a workflow file; a new target of an existing platform is verified without editing any
  workflow.
- Every workflow runs on every push and pull request, whatever the branch, and can be started manually.
  Branches that predate a workflow do not contain it, so pushing them does not run it. A newer push to the
  same branch or pull request cancels that workflow's run in progress.
- Every workflow starts with a `Detect changes` job and ends with a `<workflow name> result` job, such as
  `CI (core) result`:
  - `Detect changes` compares a push with the commit the branch pointed to before it, and a pull request with
    where it branched off its base. When every changed file is Markdown outside any `src/` folder and was
    modified or added, the workflow skips every other job. A deleted or renamed file, Markdown under `src/`,
    any other file, a manual run, or a push it cannot compare, such as the first push of a new branch, runs
    everything. The rules and their test are in `.github/actions/detect-changes/`.
  - A build script that packages Markdown from outside `src/` must also exclude those files in
    `detect-changes.sh`, or a change to them alone would skip CI.
  - The result job always runs. It passes when every job passed or was skipped, and fails when any failed or
    was cancelled. A required status check should name the result jobs: a workflow skipped by path filters
    leaves its checks pending, and the per-target job names change with the targets.
- The steps the workflows share are composite actions under `.github/actions/`:
  - `list-targets` runs `./gradlew writeCiTargetMatrix "-PciPlatform=<platform>"`, which writes
    `build/ci/target-matrix.json`: the platform's targets from the `targets.toml` files, or the built-in
    `core` target for `core`, each with the JDKs it declares and whether it has example modules.
  - `setup-target` installs the JDKs the target declares, its `java-toolchain` and any
    `additional-java-toolchains`, then JDK 25 to run Gradle, and sets up Gradle. Gradle finds the target's
    JDKs through `org.gradle.java.installations.fromEnv`; toolchain downloads are disabled, so a missing JDK
    fails the job. Pushes save the Gradle cache; pull requests only read it.
  - `verify-target` runs the same full verification as a local check with [test coverage](#test-coverage)
    measured, with any further arguments the platform's workflow passes:

    ```shell
    ./gradlew build detektAll collectCiArtifacts :koverXmlReport :koverHtmlReport --continue --stacktrace "-PmahjongcraftTarget=<target>" "-PmahjongcraftCoverage=true"
    ```

    The tests run once, with coverage measured. A failed run uploads its test and detekt reports. A passing run
    uploads the coverage report as `coverage-<target>` and the files `collectCiArtifacts` gathered into
    `build/ci/artifacts/`; a target without distributable files, such as `core`, uploads only the coverage
    report. Each platform's build convention decides what is distributable. All uploads are kept for seven days
    and are not releases.
- Each target is verified in its own job. CI sets the `CI` environment variable, so modules that start Koin
  recheck their whole dependency graph on every build, as `-PmahjongcraftKoinStrictSafety=true` does locally.
- What a platform's workflow adds is described in that platform's build guide; for Minecraft see
  [Continuous Integration](minecraft-build.md#continuous-integration).
