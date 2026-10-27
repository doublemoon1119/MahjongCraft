# Minecraft Presentation

These rules apply when changing how tiles, melds, set-aside tiles and table props are placed or animated on the
Minecraft table. They come from collisions that only showed up in game, so check them even when a change looks
local.

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

- Do not reorder a hand that is not shown to other players; lay it down or conceal it where it stands.
- A tile still in the drawn slot when a round ends stays in its slot. Sort only the standing tiles that are
  revealed.

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
