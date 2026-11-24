# Shared Minecraft Mod Sources

## Purpose

This folder holds the code, resources and tests that call Minecraft but are the same for every supported
Minecraft version, and use no loader API. It is not a Gradle project: each version's mod module, such as
[Minecraft 1.20.1 mod](../v1.20.1/mod/README.md), compiles it together with its own sources, and the loader
modules of that version compile both. The code uses the `com.doublemoon1119.mahjongcraft.platform.minecraft.mod`
package.

## Responsibilities

- Hold loader-independent Minecraft code, such as entities, blocks, items, screens, renderers and save data.
- Hold resources whose format does not change between Minecraft versions, such as the core shaders.
- Hold tests for that code; they run once in each version's mod module.

## Boundaries

- Where Minecraft versions differ, call an adapter or extend a base class that every version's mod module
  provides with the same name, mostly in the `vanilla` package, instead of calling the differing API directly.
  The naming of those adapters is described in [Minecraft Build](../../../docs/contributing/minecraft-build.md#version-mod-modules).
- Data pack files whose format or folder name changes between Minecraft versions, such as recipes,
  advancements, loot tables and item tags, belong in each version's mod module.
- Logic that does not need Minecraft classes belongs in [minecraft-common](../common/README.md).

## Testing

The tests run with each version's mod module, for example:

```shell
./gradlew :minecraft_v1.20.1_mod:test "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```
