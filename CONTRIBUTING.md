# Contributing

This file is the entry point for contributing to MahjongCraft. The detailed conventions live in
[`docs/contributing/`](docs/contributing/). Before making a change, read every guide listed below for the
kind of change you are making; the rules in them apply even when this file does not repeat them.

## Guides

- [Code style](docs/contributing/code-style.md): any Kotlin code, comments or KDoc.
- [Correctness principles](docs/contributing/correctness.md): every change. They cover client input,
  visibility, match progress and failure, contracts, identifiers, extension points and testability.
- [Extensions and registries](docs/contributing/extensions-and-registries.md): registration IDs,
  registries, Koin definitions, extension bootstrap and public API compatibility.
- [Event hooks](docs/contributing/event-hooks.md): public match events, visibility, delivery order and
  session lifetime.
- [Architecture](docs/contributing/architecture.md): module responsibilities, dependencies, and where
  code belongs.
- [Build](docs/contributing/build.md): Gradle configuration, platform targets, release versions, examples
  and CI.
- [Persistence](docs/contributing/persistence.md): anything stored on disk.
- [Minecraft build](docs/contributing/minecraft-build.md): build settings of the Minecraft modules, including
  mod dependency requirements, mod versions and development runtime mods.
- [Minecraft presentation](docs/contributing/minecraft-presentation.md): how tiles, melds and table props are
  placed and animated on the Minecraft table, including the local player's own hand, and how screens
  are laid out.

## Before Committing

- Review the change against the [review checklist](docs/contributing/review-checklist.md). Use the same
  checklist when reviewing someone else's change.
- Run `./gradlew build` — it compiles, tests, and lints (ktlint, `intellij_idea` code style per
  `.editorconfig`) every currently loaded module, and also scans their source comments for
  `docs/temp/` references (see Temp File Management).
- If it fails on ktlint violations, run `./gradlew ktlintFormat` to auto-fix them instead of fixing
  them by hand.
- Run `./gradlew detektAll` before committing. It runs detekt with type resolution, limited to the rules
  enabled in `config/detekt/detekt.yml` (KDoc layout, unnecessary fully qualified names, unused private
  declarations, coroutine handling such as rethrowing a caught cancellation, and exception handling such as
  keeping the cause when wrapping an exception). `build` does not run detekt, and detekt does not auto-fix; fix
  the reported code, or suppress an intentional exception at the declaration with `@Suppress("RuleName")` and a
  comment stating the reason.
- `./gradlew build` also verifies the Minecraft language files (`platform/minecraft/common/.../lang/`)
  are sorted by translation key. If it fails, run `./gradlew sortMinecraftLangFiles` to auto-fix instead
  of reordering entries by hand. This check reads that fixed directory directly and always runs
  regardless of the currently loaded target, unlike the per-module checks above.
- "Currently loaded module" means the core modules plus whichever platform targets are active per
  `local.dev.properties`, `-PmahjongcraftTarget`, or `MAHJONGCRAFT_TARGET`; several targets can be selected
  together, separated by commas (see [Build](docs/contributing/build.md)). Run
  `./gradlew listPlatformTargets` to list valid targets. All of the checks above — including the
  `docs/temp/` scan — only see currently loaded modules; switch targets and rerun if you need to
  verify a platform module you're not currently building against.
- Use `-PmahjongcraftTarget=core` for an explicit core-only build even when local development
  settings select a Minecraft target. Use `./gradlew switchTarget -PtoTarget=<target-id>[,<target-id>...]` to
  persist a local selection, or `./gradlew clearTarget` to restore the core-only default.
- Add `-PmahjongcraftKoinStrictSafety=true` to the build before committing. Modules that start Koin then
  recheck their whole dependency graph, which catches a Koin registration another module changed without
  changing its public API. Without it, which is the default outside CI, those modules check the graph only
  when they recompile themselves, so ordinary builds do not recompile them every time.
- CI reruns the same verification for every target on pushes and pull requests (see
  [Build](docs/contributing/build.md#continuous-integration)); it does not replace this local check.
- Compiler warnings (e.g. redundant casts, deprecated calls) fail the build, in Kotlin and Java, locally
  and in CI. Fix the code. When the flagged code is intended, suppress that one warning at the declaration
  with `@Suppress("WARNING_NAME")` and state the reason in a comment or the KDoc; do not relax the warning for
  a whole module.

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
