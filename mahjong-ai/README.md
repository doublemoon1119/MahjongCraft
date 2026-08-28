# Mahjong AI

## Purpose

`mahjong-ai` provides reusable, pluggable computer-player strategies.

## Responsibilities

- Define and register `MahjongAiStrategy` implementations.
- Choose gameplay commands from an AI-visible decision context.
- Choose round-preparation submissions with deterministic fallbacks.
- Provide the built-in random strategy used by automated players and tests.

## Boundaries

Strategies receive controlled contexts and return commands or submissions. They do not mutate games directly, access Minecraft APIs, or bypass server-side validation.

## Dependencies

The module depends on [mahjong-logic](../mahjong-logic/README.md) and [mahjong-flow-common](../mahjong-flow/common/README.md).

## Testing

Tests cover registry behavior, legal decision selection, and deterministic preparation behavior. Shared logic fixtures are supplied by [testing-mahjong-logic](../testing/mahjong-logic/README.md).
