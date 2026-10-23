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
