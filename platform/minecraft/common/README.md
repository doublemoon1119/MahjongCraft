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

## Tile resource locations

Register a tile through `MinecraftMahjongExtension.registerTileAssets` using a
`MinecraftTileAssetDefinition`. Its `assetKey` remains the stable value used by tile items,
network messages, labels, and emoji mappings; it is not the texture's resource location.

Supply `MinecraftTileResources` to select a texture and a model independently, each as a full
`namespace:path` ID. For example, a texture ID `furniture:textures/tile/five.png` refers to
`assets/furniture/textures/tile/five.png`, while model ID `furniture:item/tile/five` refers to
`assets/furniture/models/item/tile/five.json`. Textures include `.png`; model IDs omit `.json`.
Model JSON texture references must also use the intended namespace.

The resource namespace need not match the tile type's namespace. An integration may explicitly
reuse MahjongCraft resources without changing its tile identity. Different asset keys may use
the same resources; the existing unique tile-type and asset-key registration rules still apply.
Standard tile keys and `unknown` are reserved and cannot be registered by an extension.

Omitting `resources` preserves the existing `mahjongcraft` texture and model paths derived from
the asset key. Built-in keys and stored item data are unchanged. Unknown keys use the existing
unknown-tile fallback; a registered but missing model may use its texture-only fallback.
Resource locations are validated during registration setup rather than silently changing an
invalid namespace. No Minecraft-version-specific `Identifier` is needed in this common layer.

In the Fabric adapter, a valid baked model supplies its own geometry, UVs, texture references,
and display transforms. It need not inherit MahjongCraft's base model or use the built-in
`cover` texture atlas. That atlas describes the tile body, including its sides and back;
it is not merely a back-face image. Only the missing-model cuboid fallback combines the
registered front texture with the built-in atlas on its other five faces.

This registration describes resource locations, not unrestricted appearance adaptation.
World layout, entity dimensions, pose-origin adjustments, and model label positions still
assume standard tile dimensions. A differently sized or positioned model does not automatically
adjust those systems. HUD, history, and other flat previews draw the separate front texture;
they do not extract the model's front-face UVs. Concealed tiles retain the unknown-tile appearance
instead of selecting a model from their hidden tile type.

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

## Dependency injection

MinecraftCommonModule scans class-owned presentation registries and assembles the bootstrap
aggregate from those same instances. Display-name registries are bound directly in this common
module; native text conversion remains in each platform adapter.
MinecraftServerModule explicitly provides server-only state and codecs whose feature
packages overlap the common scan; session cleanup remains the lifecycle services' responsibility.

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
