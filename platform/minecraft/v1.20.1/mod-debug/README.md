# Minecraft 1.20.1 Mod Debug Tools

## Purpose

`minecraft-v1.20.1-mod-debug` compiles the [shared debug tools](../../mod-debug/README.md) against Minecraft
1.20.1, together with the debug code that is specific to 1.20.1. Its Gradle project path is
`:minecraft_v1.20.1_mod-debug`.

## Responsibilities

- Hold the automated test's mixins, in the `client.debug.autotest.mixin` package, and their configuration
  `mahjongcraft.debug.mixins.json`. The mixins ignore input, keep the game running without focus and record
  each sound request, only while the test runs. The loader debug modules register the configuration:
  Fabric in `fabric.mod.json`, Forge through the mixin configurations of its development runs.
- Provide the 1.20.1 side of the debug adapters the shared debug tools call, in the `debug.vanilla` package,
  such as creating and leaving the test world, running commands, looking up loot tables and connecting bot
  players.

## Boundaries

Loader APIs do not belong here; this module compiles against vanilla Minecraft only, so using one fails its
build. Debug code that is the same for every Minecraft version belongs in the
[shared debug tools](../../mod-debug/README.md).

## Dependencies

The module depends on `:minecraft_v1.20.1_mod`, `:minecraft_common-debug` and the modules below them,
and reads the same access transformer as the mod module. Loader debug modules compile its sources and the
shared debug tools together with their own; its own JAR uses Mojang names and is never distributed.

## Testing

Run this module's tests, which include the shared debug tests, against vanilla Minecraft from the repository
root:

```shell
./gradlew :minecraft_v1.20.1_mod-debug:test "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```
