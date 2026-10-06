# Mahjong Bundled Extensions

## Purpose

`mahjong-bundled-extensions` contains the extensions that ship with MahjongCraft and are always enabled: one per bundled rule, plus the rule-neutral built-in integration.

## Responsibilities

- Register each rule bundled with MahjongCraft (for example, Riichi) through the same `MahjongExtension` callbacks that third-party extensions use.
- Cover the platform-independent part of each rule, such as rule modules, rule-specific tile types, network and persistence formats, replay projections, AI and command handlers, and settlement resolvers.
- Keep every registration of a rule inside that rule's extension, so one file lists everything the rule registers; other modules only provide the things being registered.
- Register rule-neutral built-ins, such as the built-in AI strategies, through `BuiltInMahjongExtension`, which the platform passes to the registrar as its built-in source.

## Boundaries

This module contains no platform code. Platform presentation for the same rules, such as tile assets and translated names, is registered by the platform's own extension surface under the same extension IDs.

## Dependencies

It depends only on [mahjong-extension-api](../mahjong-extension-api/README.md), which exposes the logic, AI, and Flow registration contracts it uses.

## Testing

Tests verify that each bundled extension registers its rule integrations through the extension registrar. See [CONTRIBUTING.md](../CONTRIBUTING.md) for project-wide verification commands.
