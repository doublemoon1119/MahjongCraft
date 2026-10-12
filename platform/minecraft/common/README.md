# Minecraft Common

## Purpose

`minecraft-common` contains Minecraft-facing code and resources shared across Minecraft versions
and loaders.

Its Gradle project path is `:minecraft_common`; the source directory remains `platform/minecraft/common`.

## Responsibilities

- Define Minecraft extension registries, assets, configuration, layout, text keys,
  and presentation contracts.
- Hold shared block, item, entity, and showcase resources that are currently version-compatible.
- Provide platform-facing mappings for game actions, tiles, settlements, and animations.
- Define shared history presentation, rule catalogues, achievement mappings,
  and game-event subscriptions and delivery contracts.

## Boundaries

This module avoids loader APIs and version-specific recipe formats.
Concrete entities, renderers, commands, persistence hooks, and runtime registration belong to
loader modules.

## Dependencies

It depends on [mahjong-logic](../../../mahjong-logic/README.md),
[mahjong-ai](../../../mahjong-ai/README.md),
[mahjong-flow-common](../../../mahjong-flow/common/README.md),
[network DTOs](../../../mahjong-flow/network-dto/README.md),
coroutines, Koin, KToml, and Kotlin Serialization.

## Testing

Common and JVM tests cover registries, layout, configuration codecs, animation calculations,
language files, and resource consistency.

Run from the repository root with a Minecraft target selected:

```shell
./gradlew :minecraft_common:check "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

The module is currently configured with a JVM target.
Its JVM tests also load recipe resources from the version-common module.
Use the [complete Minecraft target build](../README.md) to verify packaging and the loader integration;
this focused check does not replace it.
