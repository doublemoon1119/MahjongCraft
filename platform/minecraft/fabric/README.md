# Shared Fabric Sources

## Purpose

This folder holds the Fabric adapter code and tests that are the same for every supported Minecraft version.
It is not a Gradle project: each version's Fabric module, such as [Minecraft 1.20.1 Fabric](../v1.20.1/fabric/README.md)
and [Minecraft 1.21.1 Fabric](../v1.21.1/fabric/README.md), compiles it together with its own sources. The code
uses the `com.doublemoon1119.mahjongcraft.platform.fabric` package.

## Responsibilities

- Register Fabric blocks, items, entities, commands, networking, and lifecycle hooks.
- Render tables, tiles, animations, settlements, and showcases.
- Connect authoritative flow services to Minecraft persistence and player interaction.
- Provide history storage and queries, player-facing screens, administrator tools,
  and Fabric context access for game-event listeners.
- Provide the ModMenu integration and the Iris shadow pass check.

## Boundaries

- Fabric-specific APIs remain in the Fabric modules. Reusable rules and flow behavior belong in core modules;
  code that calls Minecraft without a loader belongs in the [shared mod sources](../mod/README.md), and
  cross-loader Minecraft contracts and assets in [minecraft-common](../common/README.md).
- Where the Fabric API differs between Minecraft versions, such as networking, each version's Fabric module
  provides the part with the same name; mod metadata, the access widener, the mixin configuration and data
  pack files also stay in each version's module.

## Dependency injection

The common module holds runtime metadata and the services both graphs share; server and client
adapters live in separate modules. The Koin compiler plugin checks each application entry point's
complete graph at build time. Formal graph tests reject duplicate bindings and verify registry
identity and factory lifetimes.

## Unreadable saved data

The world always loads, even when saved mahjong data cannot be read.

- Rooms, games and unsaved history (`data/mahjongcraft_authoritative_state.dat`): mahjong is
  disabled for the session. Every authoritative write is refused, tables cannot be broken by
  players, managed tiles keep their entities, and the file is left untouched.
- Table locations (`data/mahjongcraft_table_locations.dat`): the index is rebuilt as tables load
  and replaces the unreadable file on the next save; mahjong stays available.
- History database (`mahjongcraft/history.sqlite`): recording and browsing history pause; games
  continue.

Players who try to use mahjong see a short message on the action bar. Administrators and the
single-player host receive the file, the likely cause and recovery steps in chat when they join.
When an extension that provided saved types is missing, reinstall it and restart. Moving the file
away is a last resort that discards every room, game in progress and unsaved history; back it up
first.

## Extension bootstrap

Discovery materializes the extension entrypoints once and composes Core, Minecraft common,
and Fabric registration in that order. Each bootstrap runs built-in callbacks first, records
each extension's additions, and freezes its registry aggregate only after that layer succeeds.
Extension IDs must be namespaced and unique within the layer.

Fabric's aggregate holds the same registry instances used by gameplay services.
Built-in registrations belong to `BuiltInNativeMahjongExtension`; the bootstrap does not
construct replacement registries or provide a reduced initialization path for tests.
Failure aborts startup without rolling back completed layers or retrying partial registration.

## Mahjong tables

A Fabric table implements the [common table contract](../../common/README.md#table-contract) on its
controller block entity and `MahjongTableStructureBlock` on every block of the table, including the
controller, which returns its own position. MahjongCraft uses the structure block to find the table
when any of its blocks is broken; the break policy then applies to the whole table.

The table's own callbacks call `MahjongTables`; no built-in table class needs to be extended:

```kotlin
override fun use(/* ... */): InteractionResult = MahjongTables.interact(world, pos, state, player)

// In the controller block; pos is the controller position.
override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
    if (!state.`is`(newState.block)) MahjongTables.onControllerRemoved(world, pos)
    super.onRemove(state, world, pos, newState, moved)
}
```

Call `onControllerRemoved` once per removal, while the controller block entity still exists.
The block entity forwards the state to `readNbt` and `writeNbt` from its own save and load methods;
MahjongCraft chooses the NBT keys. `MahjongTables.playSpaceCells` lists the play layer for checking
a model during design. These functions are specific to Fabric; another loader provides its own.

## Seat providers

Fabric integrations can register a fixed-seat provider through
`SeatProviderRegistrar`. Register an ID in the integration's namespace during extension
initialization; MahjongCraft freezes the registry once every extension has registered:

```kotlin
registry.register(RegisteredSeatProvider("example:furniture", provider, priority = 0))
```

The provider resolves only the requested table seat. It may recognize a block, an entity, or a
particular slot on a multi-seat object, but it must not scan for a replacement chair or claim a
foreign passenger. The returned `SeatTarget` must expose a stable `SeatIdentity`, finite
geometry, occupancy inspection, and a revalidated `trySit` operation.

Seat providers run on the server thread and own the Minecraft-specific work: checking loaded
chunks and collision space, moving the rider, and creating or removing only resources owned by
the provider. A normal refusal must leave the rider unchanged. If another passenger occupies
the target, return `OCCUPIED`; if space is blocked, return `BLOCKED` or `FAILED` as appropriate.

The registry is a Fabric API. An integration targeting another loader must use that loader's
corresponding registry and adapter. MahjongCraft does not
maintain compatibility adapters for every furniture mod, and registering a provider does not
grant permission to remove or control entities owned by another mod.

Opening a game currently resolves online players only; world AI representatives are a separate
integration. Built-in stools accept riders already in their world. Cross-world players may use
the checked standing fallback, but are not first teleported into a stool to attempt mounting.
Non-player cross-world transfer remains the responsibility of the entity's lifecycle adapter.

Provider exceptions stop automatic seating for that rider because occupancy could not be
confirmed. Other riders are still processed. Recovery is deliberately limited: MahjongCraft
does not forcibly remove an unknown vehicle or search the world for another safe position.
