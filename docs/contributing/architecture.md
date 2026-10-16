# Architecture

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
  - `:mahjong-flow:mahjong-flow-persistence-format`: Serializable persisted state, codecs, migrations, and domain mappings.
- **Characteristics**:
  - Depends on `:mahjong-logic`.
  - Core of asynchronous operations — introduces Coroutines for non-blocking workflows.
  - Repository contracts live in Flow; platform adapters provide their runtime implementations.

The source directories remain `mahjong-flow/common`, `client`, `server`, `network-dto`, and `persistence-format`.
Their Gradle project names are deliberately prefixed to avoid duplicate artifact names such as `common`.

### `:mahjong-extension-api`

Public extension registration layer.

- **Purpose**: Exposes typed bootstrap and registrar contracts for rule modules, commands, DTO codecs, AI strategies, and server handlers.
- **Package**: `com.doublemoon1119.mahjongcraft.extension.*`
- **Dependencies**: Provides a facade over selected APIs from `:mahjong-logic`, `:mahjong-ai`, and the Flow common, server, network DTO, and persistence format modules.
- **Boundary**: Registration does not grant direct access to authoritative mutation or platform render callbacks. Platform-specific extension surfaces belong to their platform module.

### `:mahjong-bundled-extensions`

Rule extensions that ship with MahjongCraft.

- **Purpose**: Registers each rule bundled with MahjongCraft (for example, Riichi) through the same `MahjongExtension` callbacks that third-party extensions use, and registers rule-neutral built-ins through `BuiltInMahjongExtension`. Bundled extensions are always enabled.
- **Package**: `com.doublemoon1119.mahjongcraft.bundled.*`
- **Dependencies**: Only `:mahjong-extension-api`.
- **Boundary**: Contains no platform code. Every registration of a bundled rule lives in that rule's extension; other modules only provide the things being registered. Platform presentation for these rules is registered by the platform's own extension surface under the same extension IDs.

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
  - Must accept every valid `NamespacedId` wherever players enter or select an ID, such as commands or text input, quoting or escaping it when the platform's input syntax requires.

## Dependency Rules

All modules must strictly follow the rules below to form a one-way dependency chain.

- **Core direction**: `platform` -> `:mahjong-flow` -> `:mahjong-logic` remains the authoritative application and domain dependency direction.
- **Supporting modules**:
  - `:mahjong-ai` depends on logic and Flow common contracts; Flow server and platform composition may use its strategies.
  - `:mahjong-extension-api` intentionally exposes selected logic, AI, and Flow registration contracts as a typed public facade.
  - `:mahjong-bundled-extensions` depends only on `:mahjong-extension-api`; platform composition registers its extensions ahead of third-party ones.
  - Flow network DTO and persistence format modules depend inward on logic and Flow common, and are used only at their explicit serialization boundaries.
  - JSON belongs to the persistence format, network DTO, and platform adapter code. Logic, AI, Flow common, server and client must not depend on `kotlinx.serialization.json`, and the extension API and bundled extensions must not use it; `verifyJsonFormatBoundary` checks their production sources.
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
