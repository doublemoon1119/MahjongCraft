# Mahjong Flow Common

## Purpose

`mahjong-flow-common` defines application state and contracts shared by clients and servers.

## Responsibilities

- Model games, rooms, timers, decisions, round preparation, and pending transitions.
- Define event, snapshot, synchronization, and presentation request contracts.
- Provide dependency-injection modules for shared services.

## Boundaries

This module does not execute authoritative commands, persist files, send packets, or render platform UI.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md), coroutines, and Koin.

## Testing

Tests cover shared models, timers, snapshots, ranking calculations, preparation state, and DI registration. Shared fixtures come from [testing-mahjong-flow](../../testing/mahjong-flow/README.md).
