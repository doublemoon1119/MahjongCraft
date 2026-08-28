# Contributing

## Code Comments

- **Language**: All comments and KDoc must be written in **Traditional Chinese (繁體中文)**.
- **Style**: Use objective descriptions of functionality. Avoid subjective tone or conversational language.
- **Completeness**: Every Kotlin declaration must have a complete comment or KDoc.

## Kotlin Development Conventions

- **Architecture**: Follow Clean Architecture principles. Keep code concise and well-structured.
- **Test Framework**: **JUnit is strictly forbidden**. Only `kotlin.test.Test` is allowed.
- **Test Naming**: Test method names must use backtick format, e.g., `` `test sorting with different regional orders` ``.

## Before Committing

- Run `./gradlew build` — it compiles, tests, and lints (ktlint, `intellij_idea` code style per
  `.editorconfig`) every currently loaded module, and also scans their source comments for
  `docs/temp/` references (see Temp File Management).
- If it fails on ktlint violations, run `./gradlew ktlintFormat` to auto-fix them instead of fixing
  them by hand.
- "Currently loaded module" means the core modules plus whichever platform target is active per
  `local.dev.properties`, `-PmahjongcraftTarget`, or `MAHJONGCRAFT_TARGET`. Run
  `./gradlew listPlatformTargets` to list valid targets. All of the checks above — including the
  `docs/temp/` scan — only see currently loaded modules; switch targets and rerun if you need to
  verify a platform module you're not currently building against.
- Use `-PmahjongcraftTarget=core` for an explicit core-only build even when local development
  settings select a Minecraft target. Use `./gradlew switchTarget -PtoTarget=<target-id>` to persist
  a local selection, or `./gradlew clearTarget` to restore the core-only default.
- A passing build does not mean warning-free: compiler warnings (e.g. redundant casts, unused
  imports) do not fail the build, so check the compiler output explicitly.
- Fix flagged warnings/violations before committing, unless they are pre-existing and unrelated to
  the current change.

## Gradle Build Configuration

- The repository tracks `gradle.properties` with conservative daemon defaults suitable for the
  multi-module build. They are maximum JVM limits rather than memory reserved at startup.
- Developers may override `org.gradle.jvmargs` in the Gradle user home
  (`~/.gradle/gradle.properties`). CI jobs should set limits appropriate for their runner instead of
  assuming the repository defaults fit every environment.
- Parallel project execution is enabled by default. Memory-constrained environments can pass
  `--no-parallel` and lower `org.gradle.workers.max` in their Gradle user properties.
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
  API each read their own version from `gradle/libs.versions.toml`. Root and testing projects remain
  `0.0.0-dev` because they are not published.

## Git Commit Conventions

- **Language**: All commit messages **must be written in English**.
- **Format**: Strictly follow **Conventional Commits**.
  - Format: `<type>(<scope>): <subject>`
  - Common types:
    - `feat`: A new feature
    - `fix`: A bug fix
    - `refactor`: Code change that neither fixes a bug nor adds a feature
    - `style`: Formatting, whitespace, semicolons, etc.
    - `docs`: Documentation only changes
    - `test`: Adding or modifying tests
    - `build`: Changes affecting the build system or external dependencies (e.g., Gradle)
    - `ci`: Changes to CI/CD configuration and scripts
  - Example:
    ```
    feat(application-server): implement AddAiPlayerUseCase

    Add a new use case that allows the room host to add an AI player.
    The AI player receives a generated UUID and is automatically marked
    as ready upon joining.
    ```

## Project Architecture

This project follows Clean Architecture, organizing code into separate modules with **Package by Feature (PBF)** inside each module.

### `:mahjong-logic`

Core business logic layer.

- **Purpose**: Pure Mahjong business rules, entities, and value objects (e.g., hand logic, rule config data classes).
- **Package**: `com.doublemoon1119.mahjongcraft.logic.*`
- **Characteristics**: A pure Kotlin module with no external framework or platform dependencies (no Minecraft, Koin, Coroutines, Serialization).

### `:mahjong-flow`

Application service layer.

- **Purpose**: Orchestrates business workflows (Use Cases) and defines data access contracts (Repositories).
- **Package**: `com.doublemoon1119.mahjongcraft.flow.*`
- **Sub-modules**:
  - `:mahjong-flow:common`: Shared contracts, models, and repository interfaces.
  - `:mahjong-flow:server`: Server-side Use Case implementations.
  - `:mahjong-flow:client`: Client-side Use Case implementations.
- **Characteristics**:
  - Depends on `:mahjong-logic`.
  - Core of asynchronous operations — introduces Coroutines for non-blocking workflows.
  - Only defines Repository interfaces; implementations belong to outer layers.

### `:testing`

Shared test utility module.

- **Purpose**: Provides cross-module test objects (Fakes, `TestCoroutineDispatchers`).
- **Structure**: Mirrors the production module hierarchy (e.g., `:testing:mahjong-logic`, `:testing:mahjong-flow`) to maintain a clear one-way dependency chain.
- **Characteristics**:
  - **Dependency rule**: Each test sub-module depends on its corresponding production module (e.g., `:testing:mahjong-logic` depends on `:mahjong-logic`). Reverse dependencies are strictly forbidden.
  - **Cross-platform support**: JVM-specific `testFixtures` are forbidden. Use pure Kotlin modules to support future Kotlin Multiplatform expansion.
  - **Zero pollution**: Contains test-only code only. Must not affect production dependency direction.

### `:platform`

Platform adaptation layer.

- **Purpose**: Contains platform-specific implementations (e.g., Minecraft, Hytale). Implements DataSource interfaces defined in `:mahjong-flow` with platform-native storage (world save, I/O). Serves as the final entry point for each platform.
- **Structure**:
  - `platform/{platform}/common`: Platform-level common abstractions and shared implementations.
  - `platform/{platform}/{version}/common`: Version-specific code (e.g., networking, world save format).
  - `platform/{platform}/{version}/{loader}`: Loader-specific entry points (e.g., Fabric mod initializer).

## Dependency Rules

All modules must strictly follow the rules below to form a one-way dependency chain.

- **Direction**: `platform` -> `:mahjong-flow` -> `:mahjong-logic`
- **No reverse dependencies**: `:mahjong-logic` must not depend on any outer layer. `:mahjong-flow` must not depend on `platform`.
- **Cross-layer access**: `platform` modules may directly depend on `:mahjong-flow` to implement DataSource interfaces. They must not depend on `:mahjong-logic` directly — domain interaction must go through `:mahjong-flow`.
- **Same-layer dependencies**:
  - Inside `platform`, concrete implementation modules (e.g., `fabric`) should depend on their corresponding common module (e.g., `common`).
  - Example: `:minecraft_v1_20_1_fabric` -> `:minecraft_v1_20_1_common` -> `:minecraft_common`.

## Temp File Management

- All temporary instructions, logic drafts, or one-shot prompt files generated during development must be placed under `docs/temp/`.
- Do not create non-code `.md` files directly in the project root or `src/` directory.
- The `docs/temp/` directory is listed in `.gitignore` and will not be tracked by git.
- Never cite a `docs/temp/` file from a source comment — it won't exist for anyone who clones the
  repo. `./gradlew build` fails if it finds one.
