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

## Shared tile-set appearance

The same `MinecraftTileAssetRegistry` also registers `MinecraftTileSetAppearanceDefinition`
and `MinecraftRuleTileAppearanceDefinition`. An appearance has three fields: its `id`,
the shared `coverTextureId`, and an optional `unknownFaceTextureId` that defaults to the built-in
unknown face. The rule definition binds a public rule module to that appearance.
These definitions are registered through `registerTileAssets` and frozen
with the existing tile definitions. Appearance IDs are not tile asset keys or item data.

Loader adapters are responsible for preparing material variants during resource reload.
Supported visible models inherit `mahjongcraft:item/mahjong_tile_base` without overriding
geometry or display transforms. The unknown model uses that same fixed base directly;
extensions do not provide a separate concealed model.
Variants replace texture slot `#0` with the visible tile's front or the appearance's unknown face,
and slot `#1` with its shared cover atlas. The particle texture also uses that cover.
The body atlas includes the sides and back, not merely the back face. A missing or unsupported
visible model uses the standard template with the same front and body textures.
A missing front uses the appearance's unknown face; an invalid complete appearance falls back
to the built-in appearance as a whole.

Tile size, thickness, origin, display transforms, table layout, and label anchors remain fixed.
Third-party resources customize material style, not these geometry contracts.
The cover atlas has a fixed layout: its back uses the base model's south-face UV rectangle
`[3, 0, 6, 4]` on Minecraft's 0–16 UV scale. A flat back preview crops this same rectangle,
or normalized coordinates `[3/16, 0, 6/16, 4/16]`; no separate back-preview image is needed.
Higher-resolution cover textures are allowed, but their face regions must keep the same
normalized layout. Front previews use the full front texture instead of the cover atlas.
To match an appearance's front background as well as its body, provide a custom unknown-face
texture; omitting it deliberately retains the built-in unknown face, not the white-dragon tile.
Concealed appearance comes only from the publicly known rule, never the concealed tile type.
Thus visible and concealed tiles share their body style without leaking hidden information.

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
