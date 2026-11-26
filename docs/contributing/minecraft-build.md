# Minecraft Build

Build rules for the Minecraft modules: the cross-version common layer, version mod modules and loader
modules. General Gradle configuration, targets, examples and CI are in [Build](build.md).

## Java and Remapping

- Minecraft version mod and loader modules use the Java policy declared by their selected platform target
  in `gradle/platform/minecraft/<version>/targets.toml`, not the core Java policy.
- Minecraft code uses Mojang's official names, which Fabric Loom modules select with
  `loom.officialMojangMappings()`. Code, comments and documentation name Minecraft classes and members
  by those names, for example `PoseStack` and `GuiGraphics`.
- The Minecraft loader convention makes every Fabric Loom remap task (`remapJar`, `remapSourcesJar`) share one
  build-wide slot, so remapping runs one task at a time while other work stays parallel. Two remap tasks running at
  once in the same daemon can fail with `ClosedFileSystemException` while reading a classpath JAR. New loader or
  version modules inherit this through the convention; do not add per-module workarounds.
- The root build script declares the Gradle plugins that Minecraft modules apply, such as Fabric Loom,
  without applying them, so every module loads the same plugin classes. Modules that apply different plugin
  sets would otherwise load Loom in separate class loaders, and Loom fails when it reaches another project, for
  example while an IDE sync creates `genSources` tasks. Declare a new loader's plugins there as well.
- ModDevGradle's tools that prepare Minecraft decompile and recompile it with the Java they run on. The version
  mod and loader conventions run them on the target's toolchain, or on Java 21 when the target is older, since
  the tools need at least Java 21. A newer Java names compiler-generated methods such as lambdas differently from
  vanilla, and development runs with mods that change those methods, such as Embeddium, then fail. The tools'
  cache does not record the Java version, so after changing it delete `~/.gradle/caches/neoformruntime` to
  recompile. CI disables toolchain downloads and installs only the JDKs a target declares, so a target whose
  toolchain is older than Java 21 lists 21 in `additional-java-toolchains` in its `targets.toml`.

## Version Mod Modules

- Each Minecraft version has one mod module, such as `platform/minecraft/v1.20.1/mod`, for the code and resources
  that call that Minecraft version but no loader API. Its build applies ModDevGradle in vanilla mode, the legacy
  plugin for 1.20.1 and the regular plugin from 1.21.1, so it compiles and tests against vanilla Minecraft with
  Mojang names and fails when code uses a loader API.
- Code, resources and tests that are the same for every supported Minecraft version live once in
  `platform/minecraft/mod`, which is a plain folder, not a Gradle project. Each version's mod module compiles it
  with its own sources through `includeSharedVersionSources()`, so the shared code is compiled and tested once
  per version. Where Minecraft versions differ, the shared code calls an adapter or extends a base class that
  each version's mod module provides with the same name, mostly in the `vanilla` package.
- Adapters use the newer Minecraft name when the newer version has a method that does the same thing: the older
  version's adapter is an extension function with that name. The shared code imports these functions, so the
  newer version declares the same names as extensions that only call the vanilla member, with
  `EXTENSION_SHADOWED_BY_MEMBER` suppressed; calls still resolve to the member. Differences without such a
  method get a neutral name that describes what the adapter does. Before using a newer name, check that the
  older version has no method with that name and a different purpose.
- Shared tests follow the same rule: where a test needs a Minecraft call that differs by version, such as
  creating saved data storage or locating a data pack folder, each version's mod module provides a test-only
  adapter with the same name in its own test sources.
- Data pack files whose format or folder name changes between Minecraft versions, such as recipes,
  advancements, loot tables and item tags, stay in each version's mod module.
- A loader that supports more than one Minecraft version with a continuing API keeps the adapter code that is
  the same for all of them in a shared folder named after the loader, such as `platform/minecraft/fabric`, which
  each version's loader module compiles through `includeSharedVersionSources()`, like the shared mod folder. Each
  version's loader module keeps what differs, such as networking, and the files every version has its own copy
  of: mod metadata, access wideners, mixin configurations and data pack files. A loader with one supported
  version keeps everything in its version folder. Forge and NeoForge are separate loaders and share nothing.
- Loader modules compile the mod module's sources and package its resources together with their own, instead of
  depending on its JAR. That JAR uses Mojang names, while each loader remaps the combined output to its own runtime
  names. The mod module's JAR is never distributed.
- Loader modules call `includeSourcesOf` from build-logic to compile another project's sources and resources with
  their own, and for a module in a Minecraft version folder also the matching shared folder.
  `includeResourcesOf` packages only another project's resources, such as the textures and translations in
  `:minecraft_common`. `includeTestSourcesOf` adds a test folder that only loader modules compile, such as
  the debug assembly test shared by the loader debug modules.
- Modules that use the version mod module's access transformer, such as Forge modules, debug modules and the
  example's version module, read it through `minecraftModAccessTransformer()`. Forge modules call
  `hideForgeDeprecationsOfVanillaApis()`, as the [Forge adapter](../../platform/minecraft/v1.20.1/forge/README.md)
  explains.
- Logic that does not need Minecraft classes belongs in the cross-version common layer instead, so the code each
  new Minecraft version copies stays small.

## Version Catalogs

- Each Minecraft version's `gradle/platform/minecraft/<version>/versions.toml` is registered by the root
  settings as a catalog named `mc` plus the version digits, for example `mc1201` for `v1.20.1`. Gradle plugins
  that build several Minecraft versions, such as Fabric Loom and ModDevGradle, stay in `libs`.

## Bundled Libraries

- The libraries a mod JAR carries are listed once, in the `minecraftModLibraries` extension of the loader and
  version mod conventions: the core modules and external libraries such as Koin, ktoml, Exposed and SQLite.
  Each loader module adds the list to `implementation` and to its own packaging, for example Loom's
  `include` or Forge's jar-in-jar, through `minecraftModLibraries.addTo`. Version mod modules and debug
  modules add the same list to `implementation` instead of repeating it.
- Packaging configurations pass `excludeRuntimeProvided = true`. It leaves out the libraries the selected
  target lists in `provided-versions`, such as the Kotlin standard library a Kotlin loader ships, and the SLF4J
  that Minecraft ships. A provided name without known modules fails the build, so a new provided library
  also needs its modules in build-logic.
- External Kotlin multiplatform libraries use their `jvm-` entries in `libs`, for example `jvm-koin-core`,
  because some packaging cannot identify the JVM variant that a common coordinate redirects to. Other modules
  keep the common coordinates.

## Mod Dependency Requirements

- Keep dependency versions and runtime requirements in the Minecraft version's catalog,
  `gradle/platform/minecraft/<version>/versions.toml`, named `require-<loader>-<dependency>`. A version used to
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
  as its JAR name `mahjongcraft-fabric-1.20.1-<version>.jar` does.
- Loaders compare this version differently: some ignore the build metadata, while others compare the whole
  string, so a mod that requires the same version as MahjongCraft must produce exactly the same version. A
  module in another directory, such as a debug module in `forge-debug`, sets its packaging's `loader` to the
  loader it runs on. Its JAR name keeps the directory name.
- A loader module's mod JAR is distributable unless its packaging sets `distributable` to `false`, as
  development-only modules and examples do. Distributable mods are what `collectCiArtifacts` gathers for CI:
  the JAR converted to run-time names, which is `remapJar` for Fabric Loom modules and `reobfJar` for
  ModDevGradle's legacy Forge modules, otherwise the plain JAR. Those modules' plain JAR keeps development
  names and is not distributable.
- `-PmahjongcraftBuildMetadata=<value>` appends `.<value>` to the metadata version and `+<value>` to the JAR
  version, for example to identify a CI build by its commit. It must be dot-separated letters, digits and
  hyphens, and it leaves project versions and the version policy unchanged.

## Development Runtime Mods

- Minecraft loader modules can declare named groups of mods that development runs load only on request,
  such as a shader mod for compatibility checks. Pass the groups as a comma-separated list:

  ```shell
  ./gradlew :minecraft_v1.20.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" "-PmahjongcraftDevMods=shaders,modmenu"
  ```

- Without the switch, or with an empty value, no group is loaded and development runs stay unchanged. The
  switch applies to the whole build: a module that declares groups fails the build for a selected group it
  does not declare and lists the groups it does declare, while a module that declares no groups, such as a
  debug module, ignores the switch. Like the example switch, it is not saved in `local.dev.properties`.
- Each loader module declares its groups in its build script through the `devMods` extension of the
  Minecraft loader convention. A group's mods are a bundle in the Minecraft version's catalog, so modules
  that offer the same group share it, for example `group("shaders", mc1201.bundles.dev.fabric.iris)`. Group
  names describe the environment, not one mod, while bundle names include the loader and name the mods, because
  each loader needs its own mods.
- Fabric Loom modules add selected groups to `modLocalRuntime`, and ModDevGradle's legacy Forge modules to
  `modRuntimeOnly`, so the mods are neither bundled nor published as dependencies. Another loader sets
  `configurationName` to its equivalent.
- When several targets are selected, one switch applies to all of them, so a loader that needs no mods for
  a group declares it empty, as Forge does for `modmenu` because its own mod list has a settings button.
- A group that loads a shader mod can also name a shader pack with `shaderPack(group = ..., pack = ...)`.
  When the group is selected, the client run puts the pack in its `shaderpacks` folder and writes the shader
  mod's settings to turn it on before the game starts, so manual runs and automated tests both run with
  shaders. The pack is a catalog entry, such as one from the Modrinth Maven repository, and is never
  bundled.
- Groups hold runtime mods only. Product code must not depend on them. An integration that compiles
  against another mod's API, such as a ModMenu settings entry point, declares that API as compile-only and
  keeps the other mod optional in its metadata; the group only supplies the mod for development runs.
- Fabric Loom removes the libraries a mod bundles in its own JAR when it prepares the mod for development
  runs, and expects them as Maven dependencies instead. When a mod's published metadata does not declare
  them, list those libraries in the group's bundle as well, at the versions the mod bundles.
- Development runs use the libraries of the target's Minecraft release. Loom would otherwise choose them for
  the Java version running Gradle, for example upgrading LWJGL on Java 19 or newer, while the runs use the
  target's Java toolchain. The loader convention sets Loom's runtime Java version to the target's toolchain
  unless a build sets it explicitly.
- A group name means the same kind of environment on every target, but each target chooses the actual mods.
  The groups a target declares, and how to use them, belong in that loader module's README.

## Automated Test Runs

Common commands, each followed by what it runs. The sections below explain each kind of run.

- Every selected target's client and server tests, with a summary report; the server tests accept the
  Minecraft EULA on your behalf (see [Automated Server Test](#automated-server-test)):

  ```shell
  ./gradlew autotest "-PmahjongcraftTarget=minecraft-v1.20.1-fabric,minecraft-v1.20.1-forge,minecraft-v1.21.1-fabric" "-PmahjongcraftAutotest=true"
  ```

- One loader's client and server tests at the same time, with a summary report: select only that target.

  ```shell
  ./gradlew autotest "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" "-PmahjongcraftAutotest=true"
  ```

- The same with the example's client test in place of the loader's own, which also covers screens that need
  extension content:

  ```shell
  ./gradlew autotest "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" "-PmahjongcraftExample=true" "-PmahjongcraftAutotest=true"
  ```

- One module's tests without a summary report; failed checks do not fail the build:

  ```shell
  ./gradlew :minecraft_v1.20.1_fabric:autotestTarget "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" "-PmahjongcraftAutotest=true"
  ```

- Only the client test, every step:

  ```shell
  ./gradlew :minecraft_v1.20.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" "-PmahjongcraftAutotest=true"
  ```

- Only some client steps, listed by id; the step that enters the test world always runs, and an id that names
  no step fails the test:

  ```shell
  ./gradlew :minecraft_v1.20.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" "-PmahjongcraftAutotest=table,replay"
  ```

- Only the server tests of the selected targets, with a summary report; this is what CI runs and it needs no
  switch:

  ```shell
  ./gradlew autotestServer "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
  ```

- Options that combine with any of the above:
  - `-PmahjongcraftAutotestParallel=<count>`: how many games run at once.
  - `-PmahjongcraftAutotestKeep=<count>`: how many reports to keep.
  - `-PmahjongcraftAutotestServerPort=<port>`: the first server port.

Each kind of run in detail:

- Development clients can test themselves without anyone at the keyboard. Pass the steps to run, or `true`
  for all of them:

  ```shell
  ./gradlew :minecraft_v1.20.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" "-PmahjongcraftAutotest=true"
  ```

- The example's loader modules take the same switch and also load the debug module, so the same steps run with
  the example extension installed; build them with `-PmahjongcraftExample=true`:

  ```shell
  ./gradlew :example-minecraft_v1.20.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-fabric" "-PmahjongcraftExample=true" "-PmahjongcraftAutotest=true"
  ```

  Some checks need an extension's content, such as the multi-choice settings screen, which no built-in rule
  uses. A run without that content records the check as skipped.
- The loader modules pass the switch to their runs as the `mahjongcraft.autotest` system property, together
  with the module name for the report. Without the switch, development runs stay unchanged. The debug tools
  read the property, so the packaged mod never runs automated tests.
- Test runs use their own run directory, `run/autotest-client`, so the test's options and worlds never touch
  the normal development client. The client always signs in as `MahjongTest` with a 1920×1080 window.
- Before the client starts, `prepareAutotestClient` writes the options that must hold from startup into the run
  directory's `options.txt`: the accessibility onboarding counts as seen and the narrator is off, so the first
  launch neither shows that screen nor reads it aloud through the system's speech, which the null sound output
  does not cover. Other options keep their previous values.
- Once the title screen has loaded, the client skips the first-launch accessibility screen if it still appears,
  for example when launched without Gradle, sets fixed display options (field of view, GUI scale, view
  bobbing, clouds), deletes the previous test world, creates a flat creative world with a fixed seed, runs the
  selected steps, writes a report and closes the game. The world
  keeps a fixed time and clear weather, and spawns no mobs. The report lives in the run directory under
  `autotest/<start time>/` as `report.md`, `report.json` and screenshots, and `autotest/latest.txt` names the
  latest folder. Closing the game early still writes a report that marks the unfinished step as aborted.
- Each run keeps only the latest 10 report folders and deletes older ones when it starts. Pass
  `-PmahjongcraftAutotestKeep=<count>` to keep a different number. Only folders named with the report time
  format are deleted; anything else under `autotest/` stays.
- While a test runs, the game ignores mouse and keyboard input, leaves the cursor free and keeps running without
  focus. Its client run sets `ALSOFT_DRIVERS=null`, so OpenAL Soft loads and plays sounds into a null output that
  nobody hears; the test records each sound request in the report. Do not minimise the window: Minecraft stops
  drawing while minimised.
- The steps and the input and sound handling live in the
  [shared Minecraft mod debug tools](../../platform/minecraft/mod-debug/README.md).

### Automated Server Test

- The dedicated server has its own automated test. It needs no switch:

  ```shell
  ./gradlew autotestServer "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
  ```

- The root `autotestServer` task runs the server test of every selected target and no client. When the runs
  have ended it writes `build/reports/autotest-server/<start time>/summary.md` and fails if a report did not
  pass or a server wrote no report. With no server test among the selected targets it starts no server and
  passes. CI runs this task for every Minecraft target (see [Continuous Integration](#continuous-integration)).
- A loader module's own task, such as `:minecraft_v1.20.1_fabric:autotestServer`, runs only that module's two
  servers and does not judge the result: it succeeds even when checks failed, so read its report.
- **The test accepts the Minecraft EULA on your behalf.** Before the first server starts,
  `prepareAutotestServer` writes `eula=true` to the test's own `run/autotest-server/eula.txt` and prints a
  warning, and every report repeats the reminder. Do not run the server test unless you agree to the
  [Minecraft EULA](https://aka.ms/MinecraftEULA). The normal `run/server` is never touched.
- The same task deletes the previous test world and writes fixed server settings: a flat world with a fixed
  seed, offline mode and peaceful creative play. The port is 25599 plus the target's position in the whole
  target catalog sorted by ID, so `minecraft-v1.20.1-fabric` uses 25599 and `minecraft-v1.20.1-forge` 25600.
  Pass `-PmahjongcraftAutotestServerPort=<port>` to start from another port.
- The test has its own run tasks and run directory, so the development server is never part of it:
  `runServer` and an example's `runServer` start as usual and run no test, with or without
  `-PmahjongcraftAutotest`.
- The test starts the server twice. The first run, `runAutotestServerStart`, plays at tables and stops in the
  middle of a match; the second run, `runAutotestServerResume`, loads the same world, checks what was restored
  and finishes the matches. Both runs write into the same report under
  `run/autotest-server/autotest/<start time>/`, which keeps the latest reports like the client test.
- Every match of the test uses a fixed seed and the beginner AI strategy, whose decisions depend only on the
  match, so every run and every target plays the same matches and takes the same time. A change to a rule, the
  AI or how tiles are dealt makes the same seed play a different match; when the test becomes long after such
  a change, pick another seed number in `AutotestServerTable`.
- `-PmahjongcraftAutotest=<step>[,<step>...]` limits the server test to the listed steps, as it does for the
  client test. Without it every step runs.
- The root `autotestServer` and `autotest` tasks copy every report, and the files each game wrote to its `logs`
  folder during the run, into the summary's folder, one subfolder per report such as
  `minecraft_v1.20.1_fabric-server/`. The summary links the reports by relative path, so the folder can be
  moved or uploaded as a whole.

### Running Every Target

- The root `autotest` task runs every selected target's client and server tests in one build. With
  `-PmahjongcraftExample=true` it runs each example's client test instead of its loader module's own client
  test: the example client loads the main mod, runs the same steps and also covers the extension's content.
  A loader module's `runClient` can still run its own client test directly:

  ```shell
  ./gradlew autotest "-PmahjongcraftTarget=minecraft-v1.20.1-fabric,minecraft-v1.20.1-forge,minecraft-v1.21.1-fabric" "-PmahjongcraftAutotest=true"
  ```

- At most two games run at the same time; pass `-PmahjongcraftAutotestParallel=<count>` to change it. Each game
  needs a few gigabytes of memory, and every client opens its own window.
- `-PmahjongcraftAutotestServerPort` sets the first server port; each loader module adds its target's position
  in the catalog, which does not depend on the selection, so servers that run together never share a port.
- Targets of different Minecraft versions can run together. With `-PmahjongcraftExample=true`, targets that
  declare no example modules run without example tests.
- A failing or crashing target does not stop the others. When every run has ended, the task writes
  `build/reports/autotest/<start time>/summary.md`, which links each report and lists the failed checks, and
  fails if any report did not pass or a run wrote no report. Only reports started during this build count,
  and the latest summaries are kept like the reports.

## Continuous Integration

`.github/workflows/ci-minecraft.yml` verifies every Minecraft target in its own job, with the steps shared by
all platforms (see [Build](build.md#continuous-integration)). On top of them it does the following.

- It passes `-PmahjongcraftExample=true` for a target that declares example modules, so the example mod is
  built and tested with the mod.
- It passes the short commit hash as [build metadata](#mod-versions), so a mod built by CI reads like
  `0.4.0-SNAPSHOT+1.20.1-fabric.a0f6307` and its JAR like
  `mahjongcraft-fabric-1.20.1-0.4.0-SNAPSHOT+a0f6307.jar`. Local builds leave it out.
- The Gradle cache it saves on pushes includes the Minecraft files Fabric Loom and ModDevGradle prepared.
- After the verification passes it runs the target's [automated server test](#automated-server-test) in a
  separate step:

  ```shell
  ./gradlew autotestServer --stacktrace "-PmahjongcraftTarget=<target>"
  ```

  The step uploads `build/reports/autotest-server/`, which holds the summary, each server's report and its
  logs, whether the test passed or not.
- **A CI run accepts the Minecraft EULA on the runner**, on behalf of the owner of the repository the
  workflow runs in, including a fork, because the server test writes `eula=true` into its run directory.
- The client test is not part of CI: it needs a display.
