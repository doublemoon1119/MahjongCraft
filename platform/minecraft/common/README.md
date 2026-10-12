# Minecraft Common

## Purpose

`minecraft-common` contains Minecraft-facing code and resources shared across Minecraft versions
and loaders.

Its Gradle project path is `:minecraft_common`; the source directory remains `platform/minecraft/common`.

## Responsibilities

- Define Minecraft extension registries, assets, configuration, layout, text keys,
  and presentation contracts.
- Hold shared block, item, entity, and showcase resources that are currently version-compatible.
- Provide platform-facing mappings for game actions, tiles, settlements, and animations.
- Define shared history presentation, rule catalogues, achievement mappings,
  and game-event subscriptions and delivery contracts.

## Seat extension contract

The `api.seating` package defines the cross-version contract for objects that can seat a
MahjongCraft participant at one of the table's fixed seat positions. A provider receives a
`SeatRequest` and returns candidates for that exact position; it must not search for a nearby
replacement, move the rider during resolution, or create a seat before `trySit`.

Each candidate exposes a stable `SeatIdentity`, `SeatGeometry`, an occupancy result, and a
revalidated `trySit` operation. Use a different `slot` in `SeatIdentity` for each seat on a
multi-seat object. Block and entity targets should use stable, comparable keys; an entity
runtime ID is not a persistent identity. `SeatGeometry` rejects non-finite values, and the
coordinator rejects targets whose position or facing does not match the requested fixed seat.

Providers must report `OCCUPIED`, `BLOCKED`, or `FAILED` without moving the rider or removing an
existing passenger. Only resources created by the provider may be cleaned up. A failed target
does not prevent the game from starting: the platform may leave the participant at their
original position or safely place them standing at the fixed seat.

The common contract is platform-neutral with respect to the world and rider types. Loader
modules expose their own registration facade and implement world lookup, collision checks,
teleportation, and the native riding mechanism. The contract does not promise integration with
every furniture mod; a third-party integration must provide its own adapter and ownership rules.

## Boundaries

This module avoids loader APIs and version-specific recipe formats.
Concrete entities, renderers, commands, persistence hooks, and runtime registration belong to
loader modules.

## Dependencies

It depends on [mahjong-logic](../../../mahjong-logic/README.md),
[mahjong-ai](../../../mahjong-ai/README.md),
[mahjong-flow-common](../../../mahjong-flow/common/README.md),
[network DTOs](../../../mahjong-flow/network-dto/README.md),
coroutines, Koin, KToml, and Kotlin Serialization.

## Testing

Common and JVM tests cover registries, layout, configuration codecs, animation calculations,
language files, and resource consistency.

Run from the repository root with a Minecraft target selected:

```shell
./gradlew :minecraft_common:check "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

The module is currently configured with a JVM target.
Its JVM tests also load recipe resources from the version-common module.
Use the [complete Minecraft target build](../README.md) to verify packaging and the loader integration;
this focused check does not replace it.
