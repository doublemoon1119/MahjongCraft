# Mahjong AI

## Purpose

`mahjong-ai` provides reusable, pluggable computer-player strategies.

## Responsibilities

- Define and register `MahjongAiStrategy` implementations.
- Choose gameplay commands from an AI-visible decision context.
- Choose round-preparation submissions with deterministic fallbacks.
- Provide the random strategy and an expected-value strategy configured for
  beginner, intermediate, and advanced information levels by the bundled
  extensions.

## Boundaries

Strategies receive controlled contexts and return commands or submissions. They
do not mutate games directly, access Minecraft APIs, or bypass server-side
validation. The Flow server owns scheduling and execution of AI decisions; this
module only supplies strategy and opponent-model behavior.

## Dependencies

The module depends on [mahjong-logic](../mahjong-logic/README.md) and
[mahjong-flow-common](../mahjong-flow/common/README.md).

## Testing

Tests cover registry behavior, legal decision selection, opponent-model
behavior, and deterministic preparation behavior. Shared logic fixtures are
supplied by [testing-mahjong-logic](../testing/mahjong-logic/README.md).

From the repository root, run
`./gradlew :mahjong-ai:check "-PmahjongcraftTarget=core"`. See
[CONTRIBUTING.md](../CONTRIBUTING.md) for project-wide checks.
