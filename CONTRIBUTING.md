# Contributing

## Code Comments

- **Language**: All comments and KDoc must be written in **Traditional Chinese (繁體中文)**.
- **Style**: Use objective descriptions of functionality. Avoid subjective tone or conversational language.
- **Completeness**: Every Kotlin declaration must have a complete comment or KDoc.

## Kotlin Development Conventions

- **Architecture**: Follow Clean Architecture principles. Keep code concise and well-structured.
- **Test Framework**: **JUnit is strictly forbidden**. Only `kotlin.test.Test` is allowed.
- **Test Naming**: Test method names must use backtick format, e.g., `` `test sorting with different regional orders` ``.
- **Test Language**: Test method names and assertion failure messages must be written in **English**, so a failing build reads consistently with the framework's own output. Comments and KDoc inside test files still follow the Traditional Chinese rule above.

### Import Conventions

- Use file-level `import` directives and short names for Kotlin declarations. Do not use avoidable fully qualified
  project names in type declarations, expressions, properties, functions, or annotations.
- When short names conflict, prefer an explicit import alias over repeating a fully qualified name in executable code.
- Fully qualified names may remain when they are intentionally stored as strings for reflection, serialization,
  component scanning, interoperability, or another identifier contract, or when an import alias cannot reasonably
  remove an ambiguity.
- Comments and KDoc may name a fully qualified declaration when the package itself is relevant to the explanation;
  otherwise, use a resolvable documentation link or short name.

## Before Committing

- Run `./gradlew build` — it compiles, tests, and lints (ktlint, `intellij_idea` code style per
  `.editorconfig`) every currently loaded module, and also scans their source comments for
  `docs/temp/` references (see Temp File Management).
- If it fails on ktlint violations, run `./gradlew ktlintFormat` to auto-fix them instead of fixing
  them by hand.
- `./gradlew build` also verifies the Minecraft language files (`platform/minecraft/common/.../lang/`)
  are sorted by translation key. If it fails, run `./gradlew sortMinecraftLangFiles` to auto-fix instead
  of reordering entries by hand. This check reads that fixed directory directly and always runs
  regardless of the currently loaded target, unlike the per-module checks above.
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

Platform adaptation and presentation layer.

- **Purpose**: Contains platform-specific implementations (e.g., Minecraft, Hytale). Implements repository and data-source interfaces defined in `:mahjong-flow` with platform-native storage, networking, rendering, and I/O. Serves as the final composition root for each platform.
- **Structure**:
  - `platform/{platform}/common`: Platform-level common abstractions and shared implementations.
  - `platform/{platform}/{version}/common`: Version-specific code (e.g., networking, world save format).
  - `platform/{platform}/{version}/{loader}`: Loader-specific entry points (e.g., Fabric mod initializer).
- **Domain access**:
  - May depend on immutable domain models, value objects, rule-neutral interfaces, and built-in identifiers from `:mahjong-logic` when adapting them for rendering, persistence, networking, or platform presentation.
  - May register platform presentation adapters for built-in rules, such as tile assets, translated names, sounds, and room configuration editors.
  - Must not perform authoritative rule decisions or mutate authoritative game state outside `:mahjong-flow` use cases and coordinators.

## Dependency Rules

All modules must strictly follow the rules below to form a one-way dependency chain.

- **Direction**: `platform` -> `:mahjong-flow` -> `:mahjong-logic`
- **No reverse dependencies**: `:mahjong-logic` must not depend on any outer layer. `:mahjong-flow` must not depend on `platform`.
- **Platform access to domain types**: `platform` modules may directly depend on `:mahjong-logic` for immutable domain models, value objects, rule-neutral contracts, and identifiers needed by adapters. This is still a one-way outer-to-inner dependency; it does not authorize the platform to own business rules.
- **Authoritative state changes**: Production platform code must route every authoritative game-state change through a `:mahjong-flow` use case or coordinator. It must not copy or mutate `TableState`, hands, tile walls, pending reactions, round progression, or equivalent state and write the result directly to a repository.
- **Rule decisions**: Production platform code must not invoke calculators, validators, analyzers, or rule policies to decide legal actions, wins, scoring, or game progression. Those decisions belong behind `:mahjong-flow` queries or use cases. Platform code may map their returned results into platform DTOs, assets, text, sounds, and rendering state.
- **Presentation adapters**: Platform code may use concrete built-in rule IDs, action IDs, and configuration types to register presentation mappings. For example, mapping a Riichi action ID to a Minecraft sound is allowed; independently deciding whether that action is legal is not.
- **Serialization boundaries**: Network and persistence adapters must use their explicit DTOs, registries, and versioned mappers. Do not serialize arbitrary domain objects directly merely because the platform can import their types.
- **No duplicate boundary models**: Do not create a second set of Flow DTOs solely to prevent renderers or presenters from reading immutable domain data. Introduce a dedicated DTO only when the network, persistence, privacy, compatibility, or lifecycle boundary requires one.
- **Development-only state setup**: Tests and development-gated debug scenarios may construct or replace authoritative state to provide deterministic fixtures. Keep this code inside an explicit test/debug boundary, prevent production player flows from calling it, and do not treat it as a precedent for normal platform mutations.
- **Same-layer dependencies**:
  - Inside `platform`, concrete implementation modules (e.g., `fabric`) should depend on their corresponding common module (e.g., `common`).
  - Example: `:minecraft_v1_20_1_fabric` -> `:minecraft_v1_20_1_common` -> `:minecraft_common`.

## Temp File Management

- All temporary instructions, logic drafts, or one-shot prompt files generated during development must be placed under `docs/temp/`.
- Do not create non-code `.md` files directly in the project root or `src/` directory.
- The `docs/temp/` directory is listed in `.gitignore` and will not be tracked by git.
- Never cite a `docs/temp/` file from a source comment — it won't exist for anyone who clones the
  repo. `./gradlew build` fails if it finds one.
