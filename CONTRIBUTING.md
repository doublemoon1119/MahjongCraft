# Contributing

## Code Comments

- **Language**: All comments and KDoc must be written in **Traditional Chinese (繁體中文)**.
- **Style**: Use objective descriptions of functionality. Avoid subjective tone or conversational language.
- **Completeness**: Every Kotlin declaration must have a complete comment or KDoc.
- **Layer vocabulary**: Comments and KDoc in `:mahjong-logic`, `:mahjong-flow`, `:mahjong-ai` and
  `:mahjong-extension-api` must describe a contract in that layer's own vocabulary. Do not define what
  something is, or what it is for, in terms of a presentation surface or platform that only some
  platforms have — HUD, screens, chat, entities, ticks, or a specific mod loader. Naming a platform is
  fine in a clause that is explicitly an example or a rationale ("for example, the Minecraft adapter
  renders these on its action HUD"); it is not fine in the sentence that defines the contract.

## Kotlin Development Conventions

- **Architecture**: Follow Clean Architecture principles. Keep code concise and well-structured.
- **Test Framework**: **JUnit is strictly forbidden**. Only `kotlin.test.Test` is allowed.
- **Test Naming**: Test method names must use backtick format, e.g., `` `test sorting with different regional orders` ``.
- **Test Language**: Test method names and assertion failure messages must be written in **English**, so a failing build reads consistently with the framework's own output. Comments and KDoc inside test files still follow the Traditional Chinese rule above.

### Import Conventions

- Use file-level `import` directives and short names for Kotlin declarations. Do not use avoidable fully qualified
  names in type declarations, expressions, properties, functions, or annotations.
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
- Run `./gradlew detektAll` before committing. It runs detekt with type resolution, limited to the rules enabled
  in `config/detekt/detekt.yml` (unnecessary fully qualified names and unused private declarations). `build` does not
  run detekt, and detekt does not auto-fix; fix the reported code, or suppress an intentional exception at the
  declaration with `@Suppress("RuleName")` and a comment stating the reason.
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
  API each read their own version from `gradle/libs.versions.toml`. The root and testing projects remain
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

### `:mahjong-ai`

Reusable computer-player strategy layer.

- **Purpose**: Defines pluggable AI strategies that choose commands and round-preparation submissions from controlled decision contexts.
- **Package**: `com.doublemoon1119.mahjongcraft.ai.*`
- **Dependencies**: Depends on `:mahjong-logic` and `:mahjong-flow:mahjong-flow-common` for domain data and application contracts.
- **Boundary**: Strategies return decisions; they must not mutate games directly or bypass server-side validation.

### `:mahjong-flow`

Application service layer.

- **Purpose**: Orchestrates business workflows (Use Cases) and defines data access contracts (Repositories).
- **Package**: `com.doublemoon1119.mahjongcraft.flow.*`
- **Sub-modules**:
  - `:mahjong-flow:mahjong-flow-common`: Shared contracts, models, events, snapshots, timing, presentation requests, and repository interfaces.
  - `:mahjong-flow:mahjong-flow-client`: Client-side state and Use Case implementations derived from authoritative updates.
  - `:mahjong-flow:mahjong-flow-server`: Authoritative Use Cases, orchestration, visibility, AI driving, and lifecycle.
  - `:mahjong-flow:mahjong-flow-network-dto`: Serializable transport DTOs, registries, and domain mappings.
  - `:mahjong-flow:mahjong-flow-persistence-dto`: Serializable persisted state, codecs, migrations, and domain mappings.
- **Characteristics**:
  - Depends on `:mahjong-logic`.
  - Core of asynchronous operations — introduces Coroutines for non-blocking workflows.
  - Repository contracts live in Flow; platform adapters provide their runtime implementations.

The source directories remain `mahjong-flow/common`, `client`, `server`, `network-dto`, and `persistence-dto`.
Their Gradle project names are deliberately prefixed to avoid duplicate artifact names such as `common`.

### `:mahjong-extension-api`

Public extension registration layer.

- **Purpose**: Exposes typed bootstrap and registrar contracts for rule modules, commands, DTO codecs, AI strategies, and server handlers.
- **Package**: `com.doublemoon1119.mahjongcraft.extension.*`
- **Dependencies**: Provides a facade over selected APIs from `:mahjong-logic`, `:mahjong-ai`, and the Flow common, server, network DTO, and persistence DTO modules.
- **Boundary**: Registration does not grant direct access to authoritative mutation or platform render callbacks. Platform-specific extension surfaces belong to their platform module.

### `:testing`

Shared test utility module.

- **Purpose**: Provides cross-module test objects (Fakes, `TestCoroutineDispatchers`).
- **Structure**: Mirrors the production module hierarchy (e.g., `:testing:mahjong-logic`, `:testing:mahjong-flow`) to maintain a clear one-way dependency chain.
- **Characteristics**:
  - **Gradle project names**: The source directories `testing/mahjong-logic` and `testing/mahjong-flow` are exposed as `:testing:testing-mahjong-logic` and `:testing:testing-mahjong-flow`.
  - **Dependency rule**: Each test sub-module depends on the production APIs needed to construct its fixtures. Production modules may consume these artifacts only from test source sets; production source sets must never depend on them.
  - **Cross-platform support**: JVM-specific `testFixtures` are forbidden. Use pure Kotlin modules to support future Kotlin Multiplatform expansion.
  - **Zero pollution**: Contains test-only code only. Must not affect production dependency direction.

### `:platform`

Platform adaptation and presentation layer.

- **Purpose**: Contains platform-specific implementations (e.g., Minecraft, Hytale). Implements repository and data-source interfaces defined in `:mahjong-flow` with platform-native storage, networking, rendering, and I/O. Serves as the final composition root for each platform.
- **Structure**:
  - `platform/{platform}/common`: Platform-level common abstractions and shared implementations.
  - `platform/{platform}/{version}/common`: Version-specific code (e.g., networking, world save format).
  - `platform/{platform}/{version}/{loader}`: Loader-specific entry points (e.g., Fabric mod initializer).
- **Platform target registration**:
  - Formal platform targets are declared in `gradle/platform-targets.toml`; directory presence alone does not register a build target.
  - The settings plugin derives each Gradle project path by removing the `platform/` prefix and replacing directory separators with underscores. Dots in version directory names remain dots.
  - For example, `platform/minecraft/v1.20.1/fabric` becomes `:minecraft_v1.20.1_fabric`.
  - Use `./gradlew listPlatformTargets` to inspect the currently supported targets instead of maintaining a duplicate list here.
- **Domain access**:
  - May depend on immutable domain models, value objects, rule-neutral interfaces, and built-in identifiers from `:mahjong-logic` when adapting them for rendering, persistence, networking, or platform presentation.
  - May register platform presentation adapters for built-in rules, such as tile assets, translated names, sounds, and room configuration editors.
  - Must not perform authoritative rule decisions or mutate authoritative game state outside `:mahjong-flow` use cases and coordinators.

## Dependency Rules

All modules must strictly follow the rules below to form a one-way dependency chain.

- **Core direction**: `platform` -> `:mahjong-flow` -> `:mahjong-logic` remains the authoritative application and domain dependency direction.
- **Supporting modules**:
  - `:mahjong-ai` depends on logic and Flow common contracts; Flow server and platform composition may use its strategies.
  - `:mahjong-extension-api` intentionally exposes selected logic, AI, and Flow registration contracts as a typed public facade.
  - Flow network and persistence DTO modules depend inward on logic and Flow common, and are used only at their explicit serialization boundaries.
  - `:testing:*` modules are test-only fixture providers and may only appear in test source-set dependencies.
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
  - Current example: `:minecraft_v1.20.1_fabric` -> `:minecraft_v1.20.1_common` and `:minecraft_common`.
  - These are Gradle project paths, not source directory paths; see the platform table above for the mapping.

## Temp File Management

- All temporary instructions, logic drafts, or one-shot prompt files generated during development must be placed under `docs/temp/`.
- Do not create non-code `.md` files directly in the project root or `src/` directory.
- The `docs/temp/` directory is listed in `.gitignore` and will not be tracked by git.
- Never cite a `docs/temp/` file from a source comment — it won't exist for anyone who clones the
  repo. `./gradlew build` fails if it finds one.
