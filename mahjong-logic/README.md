# Mahjong Logic

## Purpose

`mahjong-logic` is the platform-independent domain layer for MahjongCraft.

## Responsibilities

- Model tiles, hands, melds, walls, players, table state, and reactions.
- Define rule-module contracts and registries.
- Implement the currently available Japanese rule logic and the foundational,
  still-in-progress Taiwanese rule components.
- Calculate legal actions and shanten, and provide rule-specific hand-value,
  scoring, and round-result contracts where implemented. Taiwanese hand-value
  and settlement calculation is not implemented yet.

## Boundaries

This module does not depend on Minecraft, networking, persistence storage, UI,
application orchestration, or AI policy. Platform and application concerns
consume its domain types instead. Seat control, such as whether a human or an
AI strategy plays a seat, belongs to the application layer rather than the
player model.

## Dependencies

Production code is intentionally self-contained apart from Kotlin libraries and
generated project metadata. Test fixtures come from
[`testing-mahjong-logic`](../testing/mahjong-logic/README.md).

## Testing

Tests cover domain models, table transitions, rule configuration, wall layouts,
legal actions, scoring, yaku, yakuman, and tile interpretation.

From the repository root, run
`./gradlew :mahjong-logic:check "-PmahjongcraftTarget=core"`. See
[CONTRIBUTING.md](../CONTRIBUTING.md) for project-wide verification commands.
