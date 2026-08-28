# Mahjong Logic

## Purpose

`mahjong-logic` is the platform-independent domain layer for MahjongCraft.

## Responsibilities

- Model tiles, hands, melds, walls, players, table state, and reactions.
- Define rule-module contracts and registries.
- Implement the currently available Japanese and foundational Taiwanese rule logic.
- Calculate legal actions, shanten, hand values, scoring, and round results.

## Boundaries

This module does not depend on Minecraft, networking, persistence storage, UI, application orchestration, or AI policy. Platform and application concerns consume its domain types instead.

## Dependencies

Production code is intentionally self-contained apart from Kotlin libraries and generated project metadata. Test fixtures come from [`testing-mahjong-logic`](../testing/mahjong-logic/README.md).

## Testing

Tests cover domain models, table transitions, rule configuration, wall layouts, legal actions, scoring, yaku, yakuman, and tile interpretation. See [CONTRIBUTING.md](../CONTRIBUTING.md) for project-wide verification commands.
