# Build Logic

## Purpose

`build-logic` is an included Gradle build containing MahjongCraft convention and platform-target plugins.

The `detekt-rules` subproject provides MahjongCraft's KDoc layout checks. It is loaded only through
`detektPlugins`, not through a game module's runtime dependencies. Run `./gradlew -p build-logic build`
to test the rules and convention integration. The project-wide `detektAll` task checks multiline KDoc
opening lines and spacing before block tags; `build` does not run detekt and the rules do not auto-format.

The root settings include this build for plugin resolution and dependency substitution,
so `detektPlugins` can use the local rules JAR without publishing it.
This is a separate Gradle build, not a game module in the selected platform target.

The rules subproject applies the standard Kotlin JVM plugin already on the build classpath and uses
the included build's group and version. It does not select a separate JDK or apply the product
conventions that depend on `detekt-rules` itself.

## Responsibilities

- Configure shared Kotlin Multiplatform and JVM compilation policies.
- Apply ktlint and detekt to every Kotlin project, with detekt reading the shared rule file
  [`config/detekt/detekt.yml`](../config/detekt/detekt.yml) and a manual `detektAll` task that runs
  its type-resolved analysis outside `check`.
- Configure Minecraft version-common and loader projects.
- Provide the opt-in `mahjongcraft.event-api-abi` convention for Kotlin's built-in event API signature checks.
- Parse and validate the platform target catalog.
- Select core-only or platform-specific project graphs.
- Provide target-management and repository-verification plugins with separate responsibilities.

Repository checks include README presence, release-train version policy, sorted Minecraft language keys,
and forbidden temporary-document references in source.
`verifyModuleReadmes` checks that required files exist with the exact name;
it does not validate their prose, links, or commands.

## Boundaries

Build logic configures projects but does not contain runtime MahjongCraft code.
Formal targets are declared in [`gradle/platform-targets.toml`](../gradle/platform-targets.toml),
not inferred by scanning directories.

## Dependencies

The included build uses Gradle APIs, the Kotlin Gradle plugin, ktlint, detekt, KToml,
and Kotlin Serialization. Its serialization compiler follows Gradle's embedded Kotlin version.

## Testing

Gradle TestKit covers catalog validation, target selection and precedence, local target management,
repository verification,
and event API ABI filtering and failure detection for JVM and KMP modules. Run the included build's tests with
`./gradlew -p build-logic test`, in addition to the project-wide checks documented in
[CONTRIBUTING.md](../CONTRIBUTING.md).

Run included-build commands from the repository root.
The `build` command above also builds and tests the custom-rule subproject.
A product-target build consumes these plugins, but does not replace the included build's own TestKit
and rule tests.
