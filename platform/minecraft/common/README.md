# Minecraft Common

## Purpose

`minecraft-common` contains Minecraft-facing code and resources shared across Minecraft versions and loaders.

## Responsibilities

- Define Minecraft extension registries, assets, configuration, layout, text keys, and presentation contracts.
- Hold shared block, item, entity, and showcase resources that are currently version-compatible.
- Provide platform-facing mappings for game actions, tiles, settlements, and animations.

## Boundaries

This module avoids loader APIs and version-specific recipe formats. Concrete entities, renderers, commands, persistence hooks, and runtime registration belong to loader modules.

## Dependencies

It depends on [mahjong-logic](../../../mahjong-logic/README.md), [mahjong-ai](../../../mahjong-ai/README.md), [mahjong-flow-common](../../../mahjong-flow/common/README.md), Koin, KToml, and Kotlin Serialization.

## Testing

Common and JVM tests cover registries, layout, configuration codecs, animation calculations, language files, and resource consistency.
