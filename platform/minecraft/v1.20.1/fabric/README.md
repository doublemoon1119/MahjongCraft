# Minecraft 1.20.1 Fabric

## Purpose

`minecraft-v1.20.1-fabric` is the runnable Fabric adapter and packaging module for Minecraft 1.20.1.

Its Gradle project path is `:minecraft_v1.20.1_fabric`.

## Responsibilities

- Register Fabric blocks, items, entities, commands, networking, and lifecycle hooks.
- Render tables, tiles, animations, settlements, and showcases.
- Connect authoritative flow services to Minecraft persistence and player interaction.
- Provide history storage and queries, player-facing screens, administrator tools,
  and Fabric context access for game-event listeners.
- Package common modules and resources into the distributable mod JAR.

## Boundaries

Fabric-specific APIs remain in this module. Reusable rules and flow behavior belong in core modules;
cross-loader Minecraft contracts and assets belong in [minecraft-common](../../common/README.md).

## Dependencies

It composes the Minecraft common layers, all required logic and flow modules, the extension API
and bundled extensions, Fabric Loader/API, Fabric Language Kotlin, Loom, Koin, and KToml.
History storage uses Exposed's SQL APIs and SQLite JDBC;
this backend is not a dependency of the platform-neutral Flow modules.

## Development runs

Run from the repository root, selecting the target explicitly:

```shell
./gradlew :minecraft_v1.20.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
./gradlew :minecraft_v1.20.1_fabric:runServer "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

Client and dedicated-server runs use separate directories,
`platform/minecraft/v1.20.1/fabric/run/client` and `run/server`.
Server startup requires the usual Minecraft server setup, including EULA acceptance;
these tasks are interactive development launches, not automated tests.

## Testing

Tests cover runtime wiring, commands, rendering calculations, persistence, resources, table lifecycle,
and server services. The target build also remaps and packages the final mod artifact.

For a focused test run from the repository root:

```shell
./gradlew :minecraft_v1.20.1_fabric:test "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

Use the [complete Minecraft target checks](../../README.md) before committing.
The event API signature check covers this module's `api.event` surface;
it does not guarantee compatibility with a different Minecraft version or loader.
