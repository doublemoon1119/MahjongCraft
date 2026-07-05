# Contributing

## Code Comments

- **Language**: All comments and KDoc must be written in **Traditional Chinese (繁體中文)**.
- **Style**: Use objective descriptions of functionality. Avoid subjective tone or conversational language.
- **Completeness**: Every Kotlin declaration must have a complete comment or KDoc.

## Kotlin Development Conventions

- **Architecture**: Follow Clean Architecture principles. Keep code concise and well-structured.
- **Test Framework**: **JUnit is strictly forbidden**. Only `kotlin.test.Test` is allowed.
- **Test Naming**: Test method names must use backtick format, e.g., `` `test sorting with different regional orders` ``.

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

### `:domain`

Core business logic layer.

- **Purpose**: Pure Mahjong business rules, entities, and value objects (e.g., hand logic, rule config data classes).
- **Characteristics**: A pure Kotlin module with no external framework or platform dependencies (no Minecraft, Koin, Coroutines, Serialization).

### `:application`

Application service layer.

- **Purpose**: Orchestrates business workflows (Use Cases) and defines data access contracts (Repositories).
- **Characteristics**:
  - Depends on `:domain`.
  - Core of asynchronous operations — introduces Coroutines for non-blocking workflows.
  - Only defines Repository interfaces; implementations belong to outer layers.

### `:infrastructure`

Technical infrastructure layer.

- **Purpose**: Provides dependency injection (DI) wiring, DTO definitions, and cross-platform serialization (kotlinx.serialization).
- **Characteristics**: Depends on `:application` and `:domain`. Serves as the Composition Root for cross-platform wiring.

### `:testing`

Shared test utility module.

- **Purpose**: Provides cross-module test objects (Fakes, `TestCoroutineDispatchers`).
- **Structure**: Mirrors the production module hierarchy (e.g., `:testing-domain`, `:testing-application`) to maintain a clear one-way dependency chain.
- **Characteristics**:
  - **Dependency rule**: Each test sub-module depends on its corresponding production module (e.g., `:testing-domain` depends on `:domain`). Reverse dependencies are strictly forbidden.
  - **Cross-platform support**: JVM-specific `testFixtures` are forbidden. Use pure Kotlin modules to support future Kotlin Multiplatform expansion.
  - **Zero pollution**: Contains test-only code only. Must not affect production dependency direction.

### `:platform`

Platform adaptation layer.

- **Purpose**: Contains platform-specific implementations (e.g., Minecraft, Hytale). Implements DataSource interfaces defined in `:application` with platform-native storage (world save, I/O). Serves as the final entry point for each platform.
- **Structure**:
  - `platform/{platform}/common`: Platform-level common abstractions and shared implementations.
  - `platform/{platform}/{version}/common`: Version-specific code (e.g., networking, world save format).
  - `platform/{platform}/{version}/{loader}`: Loader-specific entry points (e.g., Fabric mod initializer).

## Dependency Rules

All modules must strictly follow the rules below to form a one-way dependency chain.

- **Direction**: `platform` -> `:infrastructure` + `:application` -> `:domain`
- **No reverse dependencies**: `:domain` must not depend on any outer layer. `:application` must not depend on `:infrastructure` or `platform`.
- **Cross-layer access**: `platform` modules may directly depend on `:application` to implement DataSource interfaces. They must not depend on `:domain` directly — domain interaction must go through `:application`.
- **Same-layer dependencies**:
  - Inside `platform`, concrete implementation modules (e.g., `fabric`) should depend on their corresponding common module (e.g., `common`).
  - Example: `:minecraft_v1_20_1_fabric` -> `:minecraft_v1_20_1_common` -> `:minecraft_common`.

## Temp File Management

- All temporary instructions, logic drafts, or one-shot prompt files generated during development must be placed under `docs/temp/`.
- Do not create non-code `.md` files directly in the project root or `src/` directory.
- The `docs/temp/` directory is listed in `.gitignore` and will not be tracked by git.
