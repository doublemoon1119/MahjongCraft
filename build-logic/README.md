# Build Logic

## Purpose

`build-logic` is an included Gradle build containing MahjongCraft convention and platform-target plugins.

## Responsibilities

- Configure shared Kotlin Multiplatform and JVM compilation policies.
- Configure Minecraft version-common and loader projects.
- Parse and validate the platform target catalog.
- Select core-only or platform-specific project graphs.
- Provide target-management and repository-verification plugins with separate responsibilities.

## Boundaries

Build logic configures projects but does not contain runtime MahjongCraft code. Formal targets are declared in [`gradle/platform-targets.toml`](../gradle/platform-targets.toml), not inferred by scanning directories.

## Dependencies

The included build uses Gradle APIs, the Kotlin Gradle plugin, ktlint, KToml, and Kotlin Serialization. Its serialization compiler follows Gradle's embedded Kotlin version.

## Testing

Gradle TestKit covers catalog validation, target selection and precedence, local target management, and repository verification. Run the project-wide checks documented in [CONTRIBUTING.md](../CONTRIBUTING.md).
