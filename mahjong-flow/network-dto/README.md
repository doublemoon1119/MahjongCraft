# Mahjong Flow Network DTO

## Purpose

`mahjong-flow-network-dto` defines serializable network representations for MahjongCraft flow data.

## Responsibilities

- Encode transport-safe messages and payloads.
- Map controlled domain and application values to DTOs and back.
- Support registered polymorphic extension payloads.

## Boundaries

DTOs carry data only. This module does not choose a network protocol, open connections, authorize commands, or mutate game state.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md), [mahjong-flow-common](../common/README.md), Kotlin Serialization, and Koin.

## Testing

Tests verify DTO round trips, timer payloads, and extension-aware serialization behavior.
