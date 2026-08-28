# Mahjong Flow Client

## Purpose

`mahjong-flow-client` maintains client-side application state derived from authoritative server updates.

## Responsibilities

- Track decision-timer state and other client-visible game data.
- Provide client-oriented services through shared flow contracts and dependency injection.

## Boundaries

The module does not own authoritative rules, accept commands, implement transport, or contain Minecraft rendering code.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md), [mahjong-flow-common](../common/README.md), coroutines, and Koin.

## Testing

Tests cover client state-store behavior and timing updates. Shared fixtures are provided by the testing modules.
