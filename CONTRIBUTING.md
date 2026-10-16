# Contributing

This file is the entry point for contributing to MahjongCraft. The detailed conventions live in
[`docs/contributing/`](docs/contributing/). Before making a change, read every guide listed below for the
kind of change you are making; the rules in them apply even when this file does not repeat them.

## Guides

- [Code style](docs/contributing/code-style.md): any Kotlin code, comments or KDoc.
- [Extensions and registries](docs/contributing/extensions-and-registries.md): registration IDs,
  registries, Koin definitions, extension bootstrap and the event API.
- [Architecture](docs/contributing/architecture.md): module responsibilities, dependencies, and where
  code belongs.
- [Build](docs/contributing/build.md): Gradle configuration, platform targets and release versions.
- [Persistence](docs/contributing/persistence.md): anything stored on disk.

## Before Committing

- Run `./gradlew build` — it compiles, tests, and lints (ktlint, `intellij_idea` code style per
  `.editorconfig`) every currently loaded module, and also scans their source comments for
  `docs/temp/` references (see Temp File Management).
- If it fails on ktlint violations, run `./gradlew ktlintFormat` to auto-fix them instead of fixing
  them by hand.
- Run `./gradlew detektAll` before committing. It runs detekt with type resolution, limited to the rules
  enabled in `config/detekt/detekt.yml` (KDoc layout, unnecessary fully qualified names and unused private
  declarations). `build` does not run detekt, and detekt does not auto-fix; fix the reported code, or suppress
  an intentional exception at the declaration with `@Suppress("RuleName")` and a comment stating the reason.
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

## Temp File Management

- All temporary instructions, logic drafts, or one-shot prompt files generated during development must be
  placed under `docs/temp/`.
- Do not create non-code `.md` files directly in the project root or `src/` directory.
- The `docs/temp/` directory is listed in `.gitignore` and will not be tracked by git.
- Never cite a `docs/temp/` file from a source comment — it won't exist for anyone who clones the
  repo. `./gradlew build` fails if it finds one.
