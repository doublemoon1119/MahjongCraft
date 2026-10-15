# Mahjong Flow Network DTO

## Purpose

`mahjong-flow-network-dto` defines serializable network representations for MahjongCraft flow data.

## Responsibilities

- Encode transport-safe messages and payloads, including game and room snapshot
  synchronization, history queries, and replays.
- Map controlled domain and application values to DTOs and back.
- Support registered polymorphic extension payloads.

## Boundaries

DTOs carry data only. This module does not choose a network protocol, open
connections, authorize commands, or mutate game state.

## Dependencies

It depends on [mahjong-logic](../../mahjong-logic/README.md),
[mahjong-flow-common](../common/README.md), and Kotlin Serialization.

## Dependency injection

The module does not depend on a DI framework. Platform composition registers the runtime
registry implementation and `NetworkJson`, the network format with its extension-aware serializers; see
[Minecraft common](../../platform/minecraft/common/README.md). DTO models remain plain serializable data;
bootstrap and codecs share the same registry instance.

## Testing

Tests verify DTO round trips, timer payloads, enum wire names, and extension-aware serialization behavior.

From the repository root, run
`./gradlew :mahjong-flow:mahjong-flow-network-dto:check "-PmahjongcraftTarget=core"`.
Actual packet transport and server admission checks are verified by their
owning adapters and services. See [CONTRIBUTING.md](../../CONTRIBUTING.md) for
full verification.
