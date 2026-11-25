# Minecraft 1.21.1 Fabric

## Purpose

`minecraft-v1.21.1-fabric` is the runnable Fabric adapter and packaging module for Minecraft 1.21.1. It compiles
the [shared Fabric sources](../../fabric/README.md) with its own, and packages common modules and resources into
the distributable mod JAR.

Its Gradle project path is `:minecraft_v1.21.1_fabric`.

## Responsibilities

- Send and receive the mod's packets as Fabric API payload types: each channel is registered in the
  direction it sends, and its payload carries the bytes the shared channel code writes.
- Hold the mod metadata `fabric.mod.json`, the access widener and the mixin configuration.

The adapter code shared by every Minecraft version is described in the [shared Fabric sources](../../fabric/README.md).

## Dependencies

It composes the Minecraft common layers, all required logic and flow modules, the extension API
and bundled extensions, Fabric Loader/API, Fabric Language Kotlin, Loom, Koin, and KToml.
History storage uses Exposed's SQL APIs and SQLite JDBC;
this backend is not a dependency of the platform-neutral Flow modules.

## Development runs

Run from the repository root, selecting the target explicitly:

```shell
./gradlew :minecraft_v1.21.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.21.1-fabric"
./gradlew :minecraft_v1.21.1_fabric:runServer "-PmahjongcraftTarget=minecraft-v1.21.1-fabric"
```

Development runs also load the [debug tools module](../fabric-debug/README.md), which provides the
`/mahjongcraft debug` commands; the packaged mod does not contain it.

Client and dedicated-server runs use separate directories,
`platform/minecraft/v1.21.1/fabric/run/client` and `run/server`.
Server startup requires the usual Minecraft server setup, including EULA acceptance;
these tasks are interactive development launches, not automated tests.

Compatibility checks can load extra mods into development runs with `-PmahjongcraftDevMods`
(see [Minecraft Build](../../../../docs/contributing/minecraft-build.md#development-runtime-mods)). This module and the
example module declare the same groups:

- `shaders`: Sodium and Iris, for checking rendering with and without a shader pack. Loom drops the libraries
  a mod bundles in its own JAR and expects them as Maven dependencies, which Iris on Modrinth does not
  declare, so the group also lists the libraries Iris bundles, at the versions it bundles. The group also
  puts the Complementary Reimagined shader pack in `run/client/shaderpacks` and turns it on before the client
  starts, so manual runs and automated tests both run with shaders.
  The group uses Iris 1.8.8 with Sodium 0.6, not the newer Iris 1.8.14 for 1.21.1: one of 1.8.14's mixins
  names its target method without parameter types, and Fabric Loader's development-run remapping fails on it.
  Installed games do not remap mixins this way, so players can use 1.8.14; check it with the packaged JAR.
- `modmenu`: ModMenu, for checking the mod list entry, whose settings button opens the client settings.

```shell
./gradlew :minecraft_v1.21.1_fabric:runClient "-PmahjongcraftTarget=minecraft-v1.21.1-fabric" "-PmahjongcraftDevMods=shaders,modmenu"
```

The ModMenu integration itself is part of the mod: it compiles against the ModMenu API without bundling
it, and `fabric.mod.json` lists ModMenu under `suggests`.

## Testing

Tests cover runtime wiring, commands, rendering calculations, persistence, resources, table lifecycle,
and server services. The target build also remaps and packages the final mod artifact.

For a focused test run from the repository root:

```shell
./gradlew :minecraft_v1.21.1_fabric:test "-PmahjongcraftTarget=minecraft-v1.21.1-fabric"
```

Use the [complete Minecraft target checks](../../README.md) before committing.
The public API signature check runs in the [Minecraft 1.21.1 mod](../mod/README.md) module, which compiles the
shared `api` packages and the `NativeMahjongExtension` and `NativeMahjongClientExtension` registration
interfaces; it does not guarantee compatibility with a different Minecraft version or loader.
