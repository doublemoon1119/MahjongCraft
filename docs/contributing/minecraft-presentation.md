# Minecraft Presentation

This guide covers Minecraft presentation and its public integration contracts. The placement and animation
rules come from collisions that only showed up in game, so check them even when a change looks local.

## Positions

- Compute final positions in `:minecraft_common` from plain data, such as `MahjongPlayerAreaLayout` for a
  player area. Loader code only claims entities, places them and schedules animations; it does not repeat the
  cursor or width calculations.
- Layout tests check final positions for overlaps across player counts, seats, table facings, melds,
  set-aside tiles and corner widths. Extend them when you add a new kind of object to the table.
- A width that reserves space for something, such as the corner area a hand yields to, must match what is
  actually placed there. Test that they agree.

## Moving objects that are already on the table

- When the layout of an area changes in the middle of a round, everything that moves goes together: the hand,
  the drawn tile, melds and set-aside tiles slide for the same duration, start together and end together.
  With the same start and end, the gap between neighbours only changes smoothly, so they never pass through
  each other on the way.
- When a new object takes space that tiles occupy now, move the tiles first and place the object after they
  finish. When an object goes away, remove it first and then move the tiles into the freed space.
- Use the same duration as the local player's own hand when the local hand moves with other objects, so the
  two views stay in step.
- Decide which seats move and when objects appear with `TableCornerLayoutPlanner`, not in the publisher, and
  cover the decision with a test that follows the real Flow call order.

## The local player's own hand

- The local hand is laid out on the client and follows server positions that arrive in separate packets, in
  any order and a few ticks apart. Waiting for consistent data is fine, but a wait must only block the case it
  protects. A tile that is still in the wall or in flight cannot have merged into the hand, so it must not hold
  the hand back.
- Test the local layout with the packet orders that happen in game: snapshot first, positions first, and a
  replacement tile that lands several ticks after the hand has moved.

## Settlement and revealed hands

- Once all standing tiles are publicly face up, use the player's supplied hand order. Flow already
  maintains that order according to the player's auto-sort preference; do not force sorting on reveal.
  Keep the drawn tile separate. While any standing tile remains upright, keep the shared order independent
  of the player's private order.
- A laydown starts from the pose already scheduled at the end of the tile's animation queue. Do not stand
  up and lay down a tile that is already face up or already scheduled to become face up. Keep the settlement
  timeline unchanged when the redundant rotation is omitted.

- Do not reorder a hand that is not shown to other players; lay it down or conceal it where it stands.
- A tile still in the drawn slot when a round ends stays in its slot. Sort only the standing tiles that are
  revealed.

## Round Info Parameters

- A rule's `RoundInfoLineProvider` may use `Number` and `WindValue` for values, `TileValue` for a
  tile face, and `Message` for a rule-owned word or status. `TileValue` uses the registered tile name and
  includes the tile emoji when one is available; an extension tile without a name falls back to its full tile
  type ID.
- Give every `Message` a complete namespaced ID and register its translation separately with
  `RoundInfoMessageDefinition(id, translationKey)` in the same `RoundInfoLineDisplayRegistry`. Registration
  rejects malformed or duplicate IDs and rejects all new entries after the registry freezes. Keep the message
  ID distinct from its translation key; never derive wording from the ID path.
- If a client receives a message ID that is not registered, it displays the full ID and logs one warning for
  that ID. An argument it cannot understand is preserved in its position as `?`, with one warning. A malformed
  JSON document still decodes to an empty list.
- See the [pairing example registration](../../example/platform/minecraft/common/src/commonMain/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/presentation/ExamplePairingRoundInfo.kt),
  the [Fabric 1.20.1 parameter builder test](../../platform/minecraft/v1.20.1/fabric/src/test/kotlin/com/doublemoon1119/mahjongcraft/platform/fabric/client/render/RoundInfoLineTextBuilderTest.kt)
  and the [example provider test](../../example/platform/minecraft/common/src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/presentation/ExamplePairingPresentationTest.kt).

## Tile Appearance Styles

- A tile style changes how one tile looks without changing its face or scoring. Riichi red fives are
  ordinary fives with a red dora component for scoring and the `mahjongcraft:riichi/red_five`
  appearance component for display.
- Register the texture with `MinecraftTileStyleAssetDefinition(styleId, tile, assetKey)` and the
  name with `TileStyleDisplayNameDefinition(styleId, tile, translationKey)`, once per base face the
  style supports. Emoji and corner labels follow the resulting asset key.
- A style can also change the tile body. Register a `MinecraftTileSetAppearanceDefinition` with the
  body texture and pass its ID as `bodyAppearanceId`; give such entries their own asset key even
  when they reuse the base face's resources. Renderers pick the body from the asset key, so
  settlement and showcase tiles follow it too. Styles without a body keep the rule's tile set
  appearance.
- A face-down tile whose face is known draws its back with the style body; otherwise it uses the
  rule's tile set appearance. Decide the back where the face is known and pass only the result:
  settlement templates receive `PresentationTile.Back(appearanceId)` in tile groups, never the
  hidden face.
- Resolve visible tiles with `TileFaceView.toAssetKey` and `TileFaceView.toDisplayText`. A style
  that has no entry for that face falls back to the base face.
- Get server-side faces from `GameVisibilityPolicy`: `tileFacesFor` for one observer, such as a
  decision prompt, and `publicTileFaces` for table-wide presentation, such as a win showcase. Never
  read the appearance component from an authoritative tile directly; rule effects may change it.

## Tile Corner Labels

- Resolve corner labels by the visible tile's asset key. Keep label lookup separate from translated
  names, emoji and texture resource locations; a missing label adds no corner text.
- Respect the player's local label setting unless the definition has `forced = true`. Forced labels
  still obey tile visibility and must not reveal a hidden face.
- Keep corner anchors aligned with the standard tile model in world, item and flat previews.
  Rendering code must not add a second set of built-in label mappings.
- The [shared extension example](../../example/platform/minecraft/common/README.md#tile-corner-labels)
  explains registration and links to the implementation and resource checks.

## Debug scenarios

- A new capability that changes what appears on the table comes with debug scenarios that show it in game,
  including the crowded cases: many melds, several set-aside tiles and a wide corner.

## Rule-owned table props

- A rule supplies the complete scene through `TablePropDescriber`. Give every object a stable,
  namespaced ID. Keep that ID when its content or position changes; do not generate a new UUID on
  each refresh. Objects missing from the next description are removed.
- Use `TablePropLocation.Seat` for a standard seat anchor, or `Center(order)` for the shared centre.
  The adapter chooses central coordinates. The centre comes from the four discard rows, independently
  of the number of players or wall faces; no rule receives reserved space by default.
- A native prop kind owns one `TablePropKindDefinition`: its ID, actual model dimensions and pure
  content validator. Register that kind once through the existing extension callback. Use the same
  dimensions for rendering and occupancy; an entity's selection box is not its model size.
- The layout checks rotated bounds in all three dimensions, including collisions between seat and
  central props. Touching layers are allowed. Central allocation is deterministic and bounded, not an
  optimal packing solver. An invalid scene or failed spawn clears the failed scene and reports the
  problem; do not silently keep stale rule indicators or drop selected objects.
- `FaceUpTile` creates a public display copy. It never borrows an actual hand, discard or wall tile's
  UUID and never participates in tile selection. Choose its appearance from the public rule context,
  while keeping the normal tile visibility policy unchanged.
- A rule selects and saves any score-affecting bonus tiles in authoritative state. Presentation only
  reads that state; refreshes, reconnection and loading never reroll it or decide scoring.
- Reserve the centre before opening dice work is queued. Release it after the whole multi-roll
  timeline, then request a scene from current state. Old sessions and opening generations cannot
  release a new reservation or restore an obsolete scene. Reload refreshes wait for the search
  area's entity chunks without forcing chunks to load.

## AI World Characters

Character appearance and mahjong strategy are separate. A character can have a portrait without a
world entity. Save the chosen description when the match starts; do not replace it with a newer
registry definition during a round change or reload. The built-in bot chooses a cosmetic face from
standard tiles when each match starts. Save that face in the match description; do not sample an
actual wall tile or reroll it when creating a world entity.

### Providing a world character

For example, a Fabric adapter registers a `FabricAiCharacterCapabilityDefinition` through
`FabricMahjongExtension.registerAiCharacterCapabilities`. Its ID matches the character definition's
`worldCapabilityId`. The built-in Mahjong Bot uses the same registration and seating path.
Third-party integrations do not need access to MahjongCraft's dependency injection.

| Handle ownership | Factory result | On release |
| --- | --- | --- |
| `OWNED` | A new entity of an unsaved type, not yet added to the world | Restore temporary control and remove the entity |
| `BORROWED` | An existing, loaded entity in the requested world | Restore temporary control; never delete or respawn it |

The factory only obtains a handle. It must not move or spawn the entity, create seat supports, or
pause native AI. Owned entity types must disable saving; otherwise a chunk reload could restore an
extra entity independently of the character manager. MahjongCraft claims its UUID before calling `prepare`. A player entity cannot act
as an AI proxy, and one entity cannot represent two AI participants at once.

The handle pauses and restores its own walking, combat, following and animation behavior. Do not
assume that enabling AI on release restores its previous state. If paused behavior is saved by the
other mod, its adapter must also recover stale control after unloading or restarting. Refuse the
borrow if that recovery cannot be guaranteed. `release` must tolerate partial preparation and an
already removed entity.

### Seating and lifetime

- Use the complete match roster to calculate seats, including offline players and AI without a
  world character. Never compress the list to the entities that happen to be available.
- New owned characters pass the shared seat preview before spawning. Actual seating rechecks the
  providers and occupancy. An occupied or blocked seat does not justify forcing a spawn or moving
  another occupant.
- A declared opening or seat-order change can arrange both humans and characters. Routine character
  recovery only arranges AI; it must not teleport humans back to their seats.
- Reconcile from authoritative matches and loaded tables. Do not wait for a repeated start event or
  force chunks to load. Keep the actor through round changes and release it when the match leaves
  the table, the table becomes unavailable, or the server stops.
- The built-in bot is a temporary, unsaved entity. It is recreated from the saved character choice,
  rather than restored independently from a second entity save.
- Missing capabilities and isolated callback failures may leave an AI without a world character.
  Its mahjong strategy continues, and its saved choice remains unchanged. Retry at a bounded rate;
  avoid repeating the same diagnostic on every retry.

### Cosmetic feedback

A successful discard can request `AiCharacterActionIds.DISCARD`. Unknown visual action IDs may be
ignored. These callbacks never submit a game command, change legal actions, or delay match progress.
`seatAssigned` reports a successful seat placement so the adapter can update its resting direction.

Adapters that support gaze mostly look at the fixed centre of their own hand area. This anchor does not use the actual
hand's faces, size or order. Give each bot a different timing cycle for briefly looking at a public
discard destination or the current participant's loaded world position; do not turn the whole table
towards one shared target. Idle arm motion is client-only, small and out of phase between bots. Never use a flying tile's private hand origin, hidden
tile faces, AI analysis or unresolved reactions to select a target. Send a new target only when it
changes, and clear it when it is no longer valid. The adapter may turn smoothly or ignore the request.
The built-in tile-bodied bot ignores gaze requests: it stays upright and faces its seat's table
anchor. Its chunky arms and short legs provide the character shape; idle arm movement and the
discard gesture provide motion without turning or tilting the whole body. Other adapters can still
implement gaze through the same handle.
All world-character callbacks run on the server thread; rendering stays in client initialization.

## Placing a seat for an AI character

A seat provider finds a seat that already exists. A seat placement provider explains how an item
creates one. Register the two capabilities separately; do not infer block or entity placement from
an existing-seat provider.

For example, a Fabric extension registers a `FabricSeatPlacementDefinition` through
`registerSeatPlacements`. Its provider matches the held item on either side without changing it,
then places it only on the server thread. Higher priority wins; ties use the complete ID. A failed
attempt does not fall through to another provider.

- The target is the clicked character's current match and assigned seat. Never accept a client-supplied
  table, participant or placement position. Reject stale bindings, occupied riders, excessive distance,
  missing chunks, blocked space and missing modification permissions.
- Use the actual player and interaction hand. `FabricSeatPlacementContext.placeBlockItem` preserves
  the BlockItem placement callbacks, block-use veto and normal creative/survival consumption. Its yaw
  parameter is the placement view, not necessarily the resulting block facing; the provider owns that
  conversion. Entity seats implement their own normal placement path and check their full footprint.
- Only the intended rider is excluded from placement obstruction, within the synchronous callback.
  Its placement-obstruction flag is restored even if the callback throws. Other physical entities, blocks,
  protection rules and world boundaries still apply. Never move a borrowed actor out of the way.
- Return true only after placement and normal item consumption have succeeded. MahjongCraft then
  rechecks the binding and seats only that actor. If seating fails, the seat stays and consumption is
  retained; do not delete another mod's placement or refund an item that was successfully placed.
- Nonphysical display entities do not occupy the placement space merely because their bounding boxes
  overlap it. Existing seats are checked separately through seat providers, including noncolliding
  entity seats such as cushions; never place another seat over one.
- Failed placement consumes no item. A throwing provider may have partially changed the world, so
  log the failure and do not retry another provider or guess how to undo its effects.
- Client input is intercepted only for supported items and publicly tracked AI actors. Tracking markers
  contain no game data and confer no authority. They are cleared on entity unload or disconnect;
  queued messages from a previous connection are ignored.

## Public API Compatibility

The [shared compatibility policy](extensions-and-registries.md#public-api-compatibility) defines how to
check and maintain public APIs. This section lists the Minecraft contracts; each module's
`publicApiAbi.includedNames` and committed `api/` signatures determine its exact protected surface.

- Minecraft common protects `api.event`, `api.character`, `api.seating`, `api.table` and
  `api.registration`, plus the exact `MinecraftMahjongExtension` entrypoint and
  `MahjongCraftInternalApi` annotation.
- Loader adapters protect their declared event, character, seating, table, table-prop and registration contracts. For
  example, the Fabric 1.20.1 adapter protects `api.event`, `api.character`, `api.seating`, `api.table`, `api.tableprop` and
  `api.registration`, plus the exact `FabricMahjongExtension` entrypoint.
- A new loader or version adapter declares its own protected scope and baseline. An existing adapter's
  baseline does not establish binary compatibility with another Minecraft version or loader.

Third-party seat providers create public identity and geometry values. MahjongCraft creates seat
requests through its internal factories. Preserve those constructor and factory boundaries when
extending the seating API.

`MahjongCraftInternalApi` marks public declarations needed only between MahjongCraft's own modules.
It requires explicit opt-in at error level. Opt in at the calling file, not across a whole module;
example integrations do not opt in. The signature remains in the ABI reference, but its internal
behavior is outside the compatibility promise.

Check shared changes against every affected Minecraft target. For example, a Fabric 1.20.1 check covers
its loaded common modules and Fabric adapter; it does not verify another loader's native types.
