# Mahjong Flow Server

## Purpose

`mahjong-flow-server` is the authoritative application layer for rooms and games.

## Responsibilities

- Validate and execute gameplay, reaction, preparation, and room commands.
- Coordinate timers, AI turns, pending transitions, settlement presentations, and round advancement.
- Apply visibility policy, publish snapshots, and manage server-session lifecycle.
- Host extensible outcome, continuation, and preparation registries.

## Boundaries

The server module is platform-neutral. It does not render Minecraft entities, parse Minecraft commands, or choose a concrete persistence backend.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md), [mahjong-ai](../../mahjong-ai/README.md), [mahjong-flow-common](../common/README.md), coroutines, and Koin.

## Testing

Unit and integration tests cover use cases, orchestration, AI driving, lifecycle recovery, visibility, settlement handoffs, and complete match flows.
