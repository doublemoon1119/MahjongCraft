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

## World panels

- Panels drawn in the world, such as round info, player info, dice results, settlements and tile popups, go
  through `WorldPanelDeferral.draw`. During world rendering it records the panel's transform and draws every
  panel after the rest of the world, clouds and weather included, from far to near. Vanilla does not sort
  entities by distance, so a panel drawn in the entity pass would hide entities drawn later behind it.
- Panel backgrounds use `WorldPanelRenderType`, which tests depth but does not write it, so everything
  behind a panel shows through. Text, portraits and tile faces inside a panel keep writing depth.
- A panel is not drawn while a shader mod renders its shadow map (`ShaderShadowPass`), so panels cast no
  shadows and the deferral never records the light's view. Outside world rendering, for example in replay
  and guide scenes, `draw` runs the panel immediately.
- The deferred draw runs after other entities have rendered. Do not let it read renderer fields that a later
  entity can overwrite; set them inside the draw or capture the values.

## Moving objects that are already on the table

- Flow publishes each transaction as an ordered list of `TableStep`s: the table state after the step plus
  what happened in it. When a tile passes through an intermediate area, such as a replacement tile that is
  drawn and then set aside, Flow adds a step for that state. Do not reconstruct missing moves on the platform.
- The platform arranges every tile for each step's state with `TableTileArrangement` and compares it with the
  last presented arrangement. `TableStepMotionPlanner` turns the differences into tile motions. Add a new kind
  of tile movement there, not as a bespoke animation in the publisher.
- Motions within a step play in three phases: tiles leaving their row first, then the rows making room, then
  tiles drawn from the wall. A discard lands before the hand closes the gap, and a drawn tile drops in only
  after its slot is free.
- Within a row, the largest group of tiles that keeps its order slides. Tiles that would cross another tile
  lift and arc over the row, so tiles never pass through each other.
- Each table has one presentation timeline. A step starts after the previous step's motions finish. Table
  props such as point sticks are placed from the step's state when the step starts, and round and player
  information updates when it ends.
- Reordering a whole hand is not a physical move. Make it instant, and do it while the tiles are face down
  when possible, so that only real tile moves animate.

## The local player's own hand

- The local hand is laid out on the client and follows server positions that arrive in separate packets, in
  any order and a few ticks apart. Waiting for consistent data is fine, but a wait must only block the case it
  protects. A tile that is still in the wall or in flight cannot have merged into the hand, so it must not hold
  the hand back.
- The local hand uses the same row rule as the shared view: tiles that keep their order slide, crossing tiles
  and the drawn tile joining the hand arc over the row, and a re-sort that keeps the same tiles snaps. When a
  tile leaves the hand, the local hand waits until the shared tiles start making room, so both views move
  together.
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

## Translation keys

- Put text that belongs to one rule under that rule: `mahjongcraft.<rule>.<feature>.*`, for example
  `mahjongcraft.riichi.action.riichi`, `mahjongcraft.riichi.settlement.han_fu` or `mahjongcraft.taiwan.tile.spring`.
  Four-player and three-player riichi share the `riichi` segment.
- A key belongs to a rule when only that rule's extension registers or uses it. Keep shared defaults in their
  shared groups even when one rule's wording inspired them: common actions such as chi, pon and kan, the
  liability indicator, room settings used by more than one rule, the generic draw name, and platform features
  such as HUD layout previews and debug tools.
- Keys whose names follow Minecraft conventions, such as `advancements.*`, `subtitles.*` and `stat.*`, keep
  those conventions.
- Keep the constants with the rule's presentation code, for example `RiichiTranslationKeys`, rather than in a
  shared key object.
- Write each constant as the full key string, such as `"mahjongcraft.riichi.yaku.pinfu"`, so a key copied from a
  language file finds its definition. Do not assemble keys at runtime from names, numbers or IDs; list the keys
  in an explicit table instead.
- Keep key constants in key objects rather than writing key strings where they are used.
- Every key in a language file must be referenced in full by production code or resources;
  `verifyMinecraftLangKeysUsed` checks this as part of `check`. Names Minecraft derives from registry IDs, such
  as `item.<namespace>.<path>`, only need the registry path. Remove a key when nothing uses it.
- Third-party extensions use their own resource namespace, as the example mod does with
  `mahjongcraft_example`.
- Map every registered ID to an explicit translation key; see [Correctness](correctness.md) for why keys are
  never derived from an ID path.
- Chinese and Japanese text does not end with a full stop (。). Text of more than one sentence puts each
  sentence on its own line with `\n` wherever it wraps, such as tooltips, catalogue cards, chat and
  confirmation screens. Text shown on a single line, such as the action bar and status lines, stays one line:
  join the sentences with a comma (，) in Chinese and a full-width space in Japanese. Question and
  exclamation marks stay. English keeps its full stops.

## Rule wording

- Shared defaults use rule-neutral wording in every language: Chow, Pung, Kong, Win and Self-draw in English,
  和牌 and 牌牆 in Chinese. A rule whose players say it differently registers its own wording for the same
  action, status or text, as riichi does for Ron, Tsumo, 榮和 and 牌山 and Taiwan does for 胡牌.
- Register wording through the registry that already owns the text: action vocabulary, round info lines,
  decision statuses or automatic control displays. Shared text without such a registry gets a rule wording ID
  in `BuiltInRuleWordingIds`, a neutral default from the built-in extension, and per-rule overrides through
  `registerRuleWordings`.
- Text shown outside a table, such as client settings, the rule catalogue screen and sound subtitles, cannot
  depend on a rule and stays neutral.
- Code keeps one fixed set of names for mahjong concepts: `Chi`, `Pon`, `Kan`, `Ron` and `Tsumo`, where `Ron`
  means winning on another player's discard in any rule. These names never need to match player wording.

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
  the [Minecraft mod parameter builder test](../../platform/minecraft/mod/src/test/kotlin/com/doublemoon1119/mahjongcraft/platform/minecraft/mod/client/render/RoundInfoLineTextBuilderTest.kt)
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
- Transparent tiles (the `mahjongcraft:tile/transparent` component) use
  `BuiltInMinecraftTileStyles.TRANSPARENT`: a face texture with the background removed and the
  nearly clear `BuiltInMinecraftTileAppearances.TRANSPARENT` body, whose `translucent = true` makes
  tiles draw without back-face culling so the pattern shows from behind. A face with no transparent
  entry keeps its normal look.
- Flat views of a face, such as the decision panel, history, the rule catalogue and settlement
  panels, use `MinecraftTileResources.previewTextureId`, which defaults to the face texture. Give a
  face that needs a different flat look its own preview texture instead of drawing over it in each
  screen; transparent faces use one with a light translucent white backdrop.
- The transparent textures, previews and item models are committed files generated by
  `./gradlew :minecraft_common:generateTransparentTileTextures`; rerun it after changing a standard
  face or the tile body, then review and touch up the results. It is not part of the build.
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

## Rule table tile layouts

- Where tiles sit on the table is shared geometry; a rule adjusts it only through a
  `TableTileLayoutProfile` registered with `registerTableTileLayoutProfiles`, keyed by rule module ID.
  Rules without a profile use `TableTileLayoutProfile.STANDARD`.
- Every placement entry point that a profile affects takes the profile as a required argument, so a new
  caller cannot silently fall back to the standard layout. Resolve it from the table's rule, never from a
  hard-coded rule check.
- Tile and table sizes are shared by all rules; a profile only moves tiles within the table.
- A profile can move the wall toward the centre and offset it into a pinwheel, and choose where melds go:
  the corner on the player's right, or between the wall and the hand. Set-aside tiles such as flowers follow
  the melds. In the second form the meld row is centred on the standing tiles, so drawing does not move it,
  and it is pulled inward where it would reach the table edge or a wall. A full meld row moves its last
  items to the right of the hand row and the hand moves left. The wall must move in far enough to leave
  that row free.
- A profile chooses whether discards form rows in front of each player or scatter across the centre.
  Scattered discards take spots from a position table chosen for each round, preferring free spots on the
  discarder's side. A placed discard never moves, and a claimed tile frees its spot.
- Hand rows and meld rows avoid the wall positions the rule reports through
  `PhysicalWallLayoutPolicy.roundReach`: the tiles still in the wall plus the positions its tiles may
  still move to this round. Rows therefore only gain room during a round and come back when the stacks in
  their way are drawn. The deal also avoids the wall as it stood before dealing, because the dealt stacks
  leave one by one while the hand is already in place. Every presentation that needs the hand position,
  such as the deal, a win or an exhaustive draw, takes the shift from `MahjongPlayerAreaLayout` instead of
  computing its own.
- Positions that stand for a seat, such as where a declaration sound plays or where an AI character looks,
  use the seat geometry or a `TableSeatAnchor`, not a tile placement that happens to sit nearby.

- The rule always supplies the draw order. Its physical wall layout may be absent. In that case dealt
  tiles, regular draws and replacement draws first appear in their destination and share the existing
  drop animation. Once on the table, each tile keeps its authoritative UUID for later moves.
  With a physical wall, presentation continues to claim existing wall entities by UUID.
- Without a physical wall, public face-up tiles from the wall or the reserved tiles, such as riichi
  dora indicators, are the real tile entities. They line up in one row at the table centre, in wall
  and reserved order, facing the dealer. A newly revealed tile drops in and the row re-centres. Rules
  do not describe these tiles as table props.
- Fixed openings can deal without dice; the centre is reserved only for an opening that rolls dice.

## Rule-owned table props

- A rule supplies the complete scene through `TablePropDescriber`. Give every object a stable,
  namespaced ID. Keep that ID when its content or position changes; do not generate a new UUID on
  each refresh. Objects missing from the next description are removed.
- Use `TablePropLocation.Seat` for a standard seat anchor, or `Center(order)` for the shared centre.
  The adapter chooses central coordinates. The centre comes from the four discard rows, independently
  of the number of players or wall faces; no rule receives reserved space by default. Tiles lying in
  the centre, such as the face-up row of a table without a physical wall, count as occupied space.
- A native prop kind owns one `TablePropKindDefinition`: its ID, actual model dimensions and pure
  content validator. Register that kind once through the existing extension callback. Use the same
  dimensions for rendering and occupancy; an entity's selection box is not its model size.
- Client scenes that are not part of the world, such as round replays, draw props from the same
  description. A client cannot use the server kind, so register a client preview with the same
  `TablePropKindDefinition` and a factory that builds a detached entity from the public content. For
  example, the Fabric client extension registers `TablePropPreviewDefinition` values through
  `TablePropPreviewRegistrar`. The framework owns and discards the entity and sets its position.
  A kind without a client preview is not drawn there and takes no space.
- The layout checks rotated bounds in all three dimensions, including collisions between seat and
  central props. Touching layers are allowed. Central allocation is deterministic and bounded, not an
  optimal packing solver. An invalid scene or failed spawn clears the failed scene and reports the
  problem; do not silently keep stale rule indicators or drop selected objects.
- `FaceUpTile` creates a public display copy. It never borrows an actual hand, discard or wall tile's
  UUID and never participates in tile selection. It carries a `TileFaceView`, so the public effects
  of its components, such as transparency, look the same as on the real tile. To copy a tile from the
  table, ask the describer's `TablePropTileFaces` for its public face; it returns nothing for a tile
  that is not public yet. Build a `TileFaceView` directly for a face that is not on the table.
- Do not use a display copy for a real tile that should be visible. Show the real tile entity instead,
  so it keeps one UUID, can move later and keeps matching-tile highlights.
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

### Joining with an existing seated actor

An integration can let an entity that already lives in the world join a waiting room. For example,
a Fabric adapter implements `NativeMahjongExtension.registerRoomParticipants` and registers a
`RoomParticipantDefinition` through the supplied registrar.

| Step | Integration responsibility | MahjongCraft responsibility |
| --- | --- | --- |
| Recognize | `describe` identifies a supported actor and supplies its current character description and default strategy | Validate the registered character, world capability and strategy |
| Authorize | `canJoin` checks the actor owner's permission and whether its current work allows mahjong | Check room admission, exclusions, capacity, its actual seat and unique room/game ownership |
| Start | The world provider borrows exactly `AiCharacterContext.participant` | Recheck the seat and freeze the match description |
| Release | Restore any temporarily changed native behavior | Release the binding; never delete, replace or forcibly dismount a borrowed participant |

Waiting positions and match seats are separate. An eligible actor can join while seated at any of
this table's four standard sides. Changing the room roster does not move the actor or require its
waiting side to match its eventual seat index. Unknown data is not a confirmed departure.

For a start with borrowed actors, compute the rule's final order and initial state once before
committing. Under the same-room capture scope, arrange the existing members with the normal seating
coordinator, then recheck their identity, appearance and final positions. Borrowed actors use the
same safe-standing fallback as players. An occupied or unsafe destination rejects the start and
leaves the room intact; restore this attempt's moves if no start was committed. After a successful
commit, do not undo positions because a later notification failed. Returning to the room restores
the original lobby roster for every control source; it does not save a second physical seat order.

Admission and start preflight run on the server thread after acquiring any same-room capture scope;
if the authoritative write cannot begin immediately, reject the old preflight and try again later.
Never run native world queries or integration callbacks under the authoritative store lock.

Both recognition callbacks are read-only, bounded server-thread work. Do not move the entity,
create a mount, pause its AI or perform blocking I/O in them. Register the character through
`MinecraftMahjongExtension.registerAiCharacters` and its world capability through
`registerAiCharacterCapabilities`. Registration never calls a factory to discover its ID.
There can be at most 64 room-participant providers; registration rejects an excess rather than
silently ignoring later providers.

Admission requires the actor to be riding furniture at one of the table's standard sides. Once the
match starts, getting off a chair nearby does not remove its binding or authorize MahjongCraft to
move it back. The world factory receives its assigned position through `context.seat`, and
`seatAssigned` updates that request when the match roster changes. A borrowed adapter decides
whether to return and how; it may use its own navigation or seating service. Explicit game-start
seating and a player's seat-item interaction still use MahjongCraft's normal safe seating checks.

Existing world participants can disappear because their integration removes, kills or moves them;
for example, an NPC mod may resume its own behavior. A missing actor leaves a waiting room when its
loaded table can confirm the absence. For ongoing
matches, `[ai-player].missing-policy` selects `abort_match` (default) or `continue_match`.
The latter preserves the AI participant without creating a replacement entity. The former uses the
ordinary aborted-match flow and reports `MinecraftMatchAbortReasonIds.AI_PLAYER_MISSING`.
A missing actor is dead, removed, not loaded in the table's world, or outside the normal nearby table
interaction range. Being off its exact seat is not absence. Shutdown and unloaded tables do not
trigger this check. These options apply to existing world participants, not ordinary owned proxies
or FACE-only characters, and do not share player-disconnection timers.

Room changes do not pin external participants to their current physical side or disable the rule's
normal opening order. Only normal room permissions and admission rules constrain those changes.
During a synchronous batch move, exclude only fellow movers with separate destination bounds from
intermediate collision checks. Other occupants and overlapping destinations remain blockers. Clean
up empty built-in seat helpers immediately when leaving them, without deleting furniture blocks.
For bundled and example seats, a leftover temporary mount without passengers does not occupy the
seat. Previews remain read-only; the seating operation cleans only its own empty mounts before
creating a replacement. Preserve occupied mounts and permanent furniture entities such as cushions.

Kicking a human or external participant records a room-local exclusion. Reseating, reconnecting,
changing strategy or restarting does not clear it. Only the host can see the exclusion list and
allow rejoining. Allowing does not directly join anyone: humans use normal room admission, and an
eligible actor still in its seat can be found by the next scan. Dissolving the room clears its
exclusions. Removing a generated, unbound AI does not create a meaningless rejoin entry.

### Finding a vacant seat

For example, server-side Fabric integration code can call `MahjongRooms.findVacancies` with
its actual loaded entity. The entity's position and authorization are verified by the server.
There is no client request for arbitrary coordinates, entity UUIDs or a global room directory.

- Searches stay in the entity's dimension, inspect only loaded nearby tables, and use the normal
  survival interaction distance to the table volume, currently 4.5 blocks. A caller can ask for a
  smaller distance, never a larger one. Searching does not load chunks.
- The actor must be recognized and authorized for each room. A loaded table alone is not permission
  to reveal its location. Rejected rooms do not return a more detailed reason.
- Work and output are bounded: currently one query per actor per 20 ticks, 16 queries per server tick,
  32 table candidates and eight results per query. These are runtime limits, not reserved capacity.
- A `RoomVacancy` is a hint, not a reservation. Results describe verified furniture at waiting sides, not final member indices. Moving, sitting down
  and starting must still pass the current checks. Do not forward discovery results to a client
  without a separate reason that client may see them.

Names in the host-only exclusion list may refresh from the known, loaded entity only while the
host tracks it in the room's dimension and its provider still authorizes participation. Otherwise
keep the last known name. Do not load chunks or expand discovery for name refreshes, and do not
rewrite match or history appearance snapshots.

### Client models and portraits

Room model and portrait choices go through the existing appearance registries. Bundled character
sources have low priority, so a third-party source can still explicitly choose a portrait or another
supported appearance. The selected character's `AiCharacterFace` supplies its small portrait; if it
cannot be drawn, its registered model can supply one before the unknown-tile fallback.
A room `ActorPreview` must match the captured character's preview capability and uses that character's
actual parameters. A capability ID is not a character ID or a translation key.

Names use the shared player-name resolver. Human players keep the existing authorized profile-name
sync. Capture only an actor's literal custom name; otherwise translate its registered character name
on the client. Live UI uses live descriptions; history uses that match's fixed description.
The host-only exclusion screen uses face cards, search and the shared scrollbar behavior, without
model previews. Only the host receives excluded actors' names and faces; ordinary observers receive
neither. The list is bounded, and reaching its limit rejects a new exclusion instead of dropping an
old one. Allow-all is one host-authorized transaction and does not join or move anyone.

For example, a Fabric integration supplies a separate `NativeMahjongClientExtension` through the
`mahjongcraft:client_extension` entrypoint. It registers `AiCharacterPreviewDefinition` values
through `AiCharacterPreviewRegistrar`. Keep this entrypoint and all native client types out of
the server extension. Third-party integrations do not need MahjongCraft's dependency injection.

Room model cards draw entities directly. Human players and other characters use the same whole-model
cursor rotation. Prefer the participant's loaded world entity, identified by a reference from the
current authorized character snapshot. Do not guess by name or proximity, load chunks, or use a
current entity when displaying history. A higher-priority explicit portrait choice still wins.
Live references are transient: send only entities already tracked by that recipient, refresh through
the existing observer path when tracking changes, and never put these references in saved appearance
parameters or history. Supplemental delivery revisions increase even if the game itself is unchanged.

When the original is unavailable, a preview factory creates an independent entity from the captured
`AiCharacterDefinition`. The optional bounded string map contains only the appearance parameters
chosen by its provider. The provider interprets them and owns its default appearance; the framework
does not save arbitrary entity NBT or require a complete reconstruction of equipment or clothing.
A factory must not return a live world entity, spawn anything, load chunks or run native AI.

Create fallback models outside UI drawing, at most one queued model per client tick. Keep at most 64
models and release their handles on eviction, world changes, disconnect or resource reload. A live
world entity is never owned by this cache or passed to a factory's `close`. Living previews use the
vanilla inventory entrypoint with the room's original placement and scale.
That entrypoint temporarily changes display angles; restore those angles even if rendering fails.
Other entities use the same coordinate handedness and baseline. Never change the original
entity's position, vehicle or animation state.
The usual F3+B setting may apply to these previews, consistently with player previews.
Keep the caller's GUI model-view transform intact; reset the origin only for offscreen capture
using its own projection. Restore graphics and dispatcher settings even when an external renderer
fails. Isolatable failures
fall back with bounded retries; cancellation and fatal errors propagate.

Small portraits may still use a captured model image when an explicit FACE is unavailable. These
images have their own bounded 64-entry, 128-by-128 cache; a portrait request does not turn a room model
card into a still image. A working explicit FACE needs no model capture. The factory may supply a
normalized portrait region. Capture-only handles are closed immediately after the image is made.

### Historical appearance

Save the actor's ordinary display name and character description when the match starts. Archive that
same description with the participant record, before releasing its retained match metadata. History
never looks up today's world entity or substitutes today's registry default for the old description.
This saves resource IDs and parameters, not copies of texture files; missing resources use fallback.

Character attachments follow the existing authorized snapshot, history-list and history-summary
paths. Recheck the table's match and roster before live delivery. Historical attachments must match
an accepted request and its returned match IDs. The list's extra appearance data has a shared size
budget; when that budget is exhausted, a missing list portrait uses fallback, and opening its summary
can request the full description. An invalid or oversized appearance does not make an otherwise
readable replay unusable. The client keeps historical descriptions separate from live table state.

For local development, `/mahjongcraft debug seated_participant` creates an ordinary armor stand at
an available furniture seat that keeps existing occupants in place in the command user's waiting room. It does not insert a room member:
normal discovery must do that. This command and its borrowed-actor adapter belong to the development-only
[debug tools module](../../platform/minecraft/v1.20.1/mod-debug/README.md) and require operator permission. They are a small contract check, not a
replacement for testing another mod's ownership, behavior restoration and furniture integration.

### Providing a world character

For example, a Fabric adapter registers an `AiCharacterCapabilityDefinition` through
`NativeMahjongExtension.registerAiCharacterCapabilities`. Its ID matches the character definition's
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
`seatAssigned` supplies the current assigned seat and facing, including after a roster change.
A borrowed adapter decides how to react or return; MahjongCraft does not force a nearby dismounted
actor back into position. The callback must return promptly, so navigation belongs in the adapter's
own updates rather than a blocking callback.

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

For example, a Fabric extension registers a `SeatPlacementDefinition` through
`registerSeatPlacements`. Its provider matches the held item on either side without changing it,
then places it only on the server thread. Higher priority wins; ties use the complete ID. A failed
attempt does not fall through to another provider.

- The target is the clicked character's current match and assigned seat. Never accept a client-supplied
  table, participant or placement position. Reject stale bindings, occupied riders, excessive distance,
  missing chunks, blocked space and missing modification permissions.
- Use the actual player and interaction hand. `SeatPlacementContext.placeBlockItem` preserves
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

## Screen design

- Build screens from the shared components in `client/gui`. They are not specific to settings screens:
  `FormScreenLayout` for screens made of category tabs, rows and a footer, `TabStripLayout` and
  `TabWidget` for tabs, `ButtonRowLayout` for a row of equal-width buttons, `DropdownOverlay` for
  selector lists, `ControlTooltip` for control tooltips, `VerticalScrollbar` and `HorizontalScrollbar`
  for scrollbars, `CardBackground` for card colours, `wrapLinesBalanced` for card text that wraps and
  `ScreenRendering` for titles, labels and status lines. Start from their sizes and colours. A screen
  that needs a different look may choose its own; when other screens could use it too, add it to the
  shared component instead, as `CardBackground` does with its neutral colours.
- Use the vanilla translucent background by default. A part of the content that needs to stand apart
  from what is behind it may have its own backing, such as the decision panel over the world during a
  match or a table scene drawn in a screen. Do not cover the whole screen with an overlay.
- Mark the current page, or a selector whose list is open, with the white outline
  (`RestartableMarqueeButtonWidget.outlined`). Keep the disabled look for buttons that cannot be pressed;
  it does not mean "you are here".
- Pages use ordinary buttons and categories use tabs. Do not give both the same look.
- A selector that changes every category, such as the rule and variant on the room settings page, sits
  above the tabs and belongs to no category.
- A selector shows only its current value. Name the control with a label beside it or in its tooltip,
  never as a prefix on the button: a prefix leaves too little room for the value, which is the part
  players need to read.
- An option that cannot be chosen stays in its place with its reason, in lists and in tooltips. Do not
  move it to the end or hide it; a fixed order keeps the list from jumping when a setting changes.
- A setting that the selected variant fixes stays in its place, disabled. Its tooltip lists every option,
  marks the fixed one as current and gives the variant's reason on the others. A rule flag that only a
  variant changes is not a setting and does not appear on the screen.
- Route every cycle button through `CycleButtonInput` and build it with `pressesOnSecondaryClick`:
  left-click selects the next option, right-click or Shift+left-click the previous one.
- A row that only performs an action and has no value is one button spanning the row, without a label.
- A screen opened from another screen goes back to it on Back, Done, Close or Esc. A screen opened by
  its own key, a command or a chat link goes back to the game. The Mahjong Handbook quick screen
  (`client/handbook`) is the shared entry point to the built-in screens; its entries are fixed, not
  registered by extensions.
- Footer buttons are equal in width and span the content width. The bottom-right slot always holds the
  button that leaves the screen (Back, Done or Close), so players find it in the same place everywhere.
- Offer Cancel only where leaving can discard something:
  - A screen that only shows information has Back or Close.
  - A screen whose controls take effect at once has Done.
  - A sub-screen that edits a temporary copy for its parent has Cancel and Done: Done writes the copy
    back, and Cancel and Esc discard it.
  - A full settings page has Reset, Undo, Apply and Done, and asks before Esc discards unapplied
    changes.
  - A confirmation names its action on one button and has Cancel on the other.
- Content on form and list screens is at most `FormScreenLayout.MAX_CONTENT_WIDTH` wide and centred.
  Screens built around one view, such as the HUD layout editor and the round replay, may use the whole
  window. Stripe every other row on screens made of rows, and put scrollbars in the margin to the right
  of the content.
- Draw icons with `PixelIcons` and `PixelIcon`, not with text symbols, so their size and weight do not
  depend on the font. A button that shows only an icon still names its action in its tooltip.
- Text that acts as a control is a button or a whole clickable row, never underlined text. Controls
  inside scrolling content are drawn and clipped with that content, and respond only inside it.
- Build control tooltips with `ControlTooltip`. List every option and mark the current one instead of
  adding a separate "current value" line. Write hints as the action and its result, such as
  "Left-click: next option", not internal concepts; translations follow the same rule. Put a button's
  label in square brackets when a hint mentions it, such as `[−]`.

## Rule catalogue

- A rule registers a `RuleCatalogueProvider` that builds its categories and entries from a rule config.
  The catalogue only explains; examples are illustrations and never go through win or scoring checks.
- Each rule should offer a Basics category under `BuiltInRuleCatalogueCategoryIds.BASICS`, named with
  `MinecraftRuleCatalogueScreenKeys.CATEGORY_BASICS`. The screen shows it before All categories, keeps it
  selected when the player switches to another rule that has it, and other screens can open the catalogue
  straight on it, as the getting started guide does.
- Write Basics text in general terms. Values a room can change say what the standard value is, and the
  entry lists the room setting fields in `adjustableSettingNameKeys`, using the fields' own name keys.
  The config only decides whether an entry applies; an entry that does not apply keeps its place with an
  unavailable reason.
- Examples can carry a subtitle (`titleTranslationKey`), and tile groups can carry their own label
  (`labelTranslationKey`) instead of the role's, such as Discards or Can't discard after chi.

## Getting started guide

- The guide screen (`client/guide`) describes each page as a list of `GuideBlock`s in `GuidePages`.
  `GuideContentLayout` places them for the current width, so new content is a new block in that list,
  not hand-placed drawing code. Every string is a translation key in `MinecraftGuideScreenKeys`.
- Content states only what holds in every rule. When an example needs a specific rule, such as
  riichi in Japanese mahjong, the text says it is an example from that rule.
- Pictures of the world are scenes (`client/guide/scene`): a `GuideScene` lists blocks, figures, tiles,
  round info and player info, and `GuideSceneRenderer` draws them with the game's own block, entity,
  tile and info renderers. Place tiles with the table layouts the game uses (`MahjongTileTableLayout`,
  `MahjongTileWallSideLayout`, `MahjongSeatingTableLayout`) so a scene matches what players see at a
  real table. Scenes are never added to a world.
- Example decision panels use `MahjongHudPreviewRenderer.renderDecisionPanelAt`, the same drawing as
  the in-game panel. Do not draw a copy of the panel.
- Recipes come from the world's recipe manager when the screen opens, so data packs that change the
  table or stool recipes are shown as they are. They are drawn on the vanilla crafting table texture,
  so resource packs that restyle it restyle the guide too.

## Round replays

- A replay lays out each step with the same `TableTileArrangement` as the live table, using the match's
  rule module on the client and the match's saved rule settings. Moves between steps come from the same
  `TableStepMotionPlanner`. Do not build a separate layout or animation for replays.
- The client calls the rule's `sideStackCounts` and `sideArrangement` with the match's saved settings, so a
  rule extension must also be installed on a client for that client to replay its matches. When the client
  does not have the match's rule, the replay buttons are disabled and their tooltips say why. When a step
  still cannot be laid out, the replay names the reason instead of showing a loading message.
- The saved replay keeps only the table state. Anything else the live table needs to place tiles or draw
  the table is worked out by the rule or the table from the full state during play, so it is recorded
  into `ReplayTableNotes` when the match is saved, and replays never call the rule for it again. Examples
  are the wall positions still to be reached from `roundReach`, the scattered discard pattern, corner
  widths, round info, player indicators, table props and which hands are revealed at an exhaustive draw.
  When the live layout starts using another such value, record it there as well; never work it out again
  on the client from the saved state.
- Win records keep the winning tile even when the hand is not revealed, and winners of one discard
  share it. The win effect and hand reveals use timing shared with the live table
  (`WinCelebrationEffectSteps`, `HandRevealSteps`), so change their pacing in one place.

## Public API Compatibility

The [shared compatibility policy](extensions-and-registries.md#public-api-compatibility) defines how to
check and maintain public APIs. This section lists the Minecraft contracts; each module's
`publicApiAbi.includedNames` and committed `api/` signatures determine its exact protected surface.

- Minecraft common protects `api.event`, `api.character`, `api.seating`, `api.table` and
  `api.registration`, plus the exact `MinecraftMahjongExtension` entrypoint and
  `MahjongCraftInternalApi` annotation.
- Loader adapters protect their declared event, character, seating, table, table-prop and registration contracts. For
  example, the Fabric 1.20.1 adapter protects `api.event`, `api.character`, `api.seating`, `api.table`, `api.tableprop` and
  `api.registration`, plus the exact `NativeMahjongExtension` and `NativeMahjongClientExtension` entrypoints.
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
