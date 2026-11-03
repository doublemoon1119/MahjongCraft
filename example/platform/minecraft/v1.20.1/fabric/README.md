# Fabric 1.20.1 Example

This is the independently packaged example mod, with ID `mahjongcraft_example`.
It depends on MahjongCraft and combines the two example libraries into its own JAR.
It does not bundle MahjongCraft, its dependencies or another copy of Kotlin.

The entrypoints load the platform-independent pairing rule and its shared Minecraft presentation.
The main initializer registers the chair, bench, cushion, actor, three Mahjong tables, creative tab and public
game-event listeners.
The client initializer registers furniture and actor renderers; a separate client extension registers
preview factories. The dedicated server does not load either client entrypoint.
Seat providers are registered through the extension callback without reading native content during
construction. World lookup and seating occur only when the provider is used.

The block chair creates a temporary invisible support only when seating succeeds. The cushion is
the seat entity itself and retains its UUID when the world is saved and loaded. Both use the public
seat contracts rather than MahjongCraft's internal stool implementation or dependency container.
See the [example guide](../../README.md) for commands and development-world isolation.

## Mahjong tables

The three tables use the [Fabric table hooks](../../../../../platform/minecraft/v1.20.1/fabric/README.md#mahjong-tables)
without extending MahjongCraft's built-in table. Each controller block entity implements
`MahjongTableController` and saves its state through MahjongCraft's NBT functions; every block of a
table implements `MahjongTableStructureBlock`. MahjongCraft finds them without a registration step.

- **One-block table:** a single controller block. Its model is drawn 3×3 wide, the largest a JSON
  model allows, while its collision and outline stay inside its own block.
- **Low table:** a 3×3 single layer with the controller in the centre. Placing it fills all nine
  blocks; breaking or replacing any of them removes the whole table, and only the broken block drops
  the item.
- **Round table:** the same structure as the low table with a round top. Its centre pedestal stays
  below the tabletop, so it never enters the play layer.

All three keep the tabletop at the top of their own blocks, so the layer above them is the play
layer. Every block waterlogs like a vanilla block when placed in a water source.

To see the play-space check, create a room at one of these tables and place a block, torch or water
on the tabletop layer: every room member sees a red outline and the host cannot start. A block
placed higher, at the height of the floating displays, does not stop the game from starting.

The loader entry points are [`ExampleMod.kt`](src/main/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/ExampleMod.kt)
and [`ExampleModClient.kt`](src/main/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/ExampleModClient.kt).
Furniture and seating behavior is covered by focused tests such as
[`ExampleCushionPlacementTest.kt`](src/test/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/ExampleCushionPlacementTest.kt),
[`ExampleFurnitureSeatRulesTest.kt`](src/test/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/ExampleFurnitureSeatRulesTest.kt),
and [`ExampleGameEventChatTest.kt`](src/test/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/event/ExampleGameEventChatTest.kt).
These tests exercise decision and geometry seams. Native world rendering still needs the manual checks
in the parent guide; packaged dedicated-server startup has already passed its smoke check.

## World actors and seat integration

[`ExampleFabricMahjongExtension.kt`](src/main/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/ExampleFabricMahjongExtension.kt)
registers participant eligibility and borrowed/owned factories through the public registrars.
The provider recognizes only `ExampleActorEntity`; `canJoin` checks the placer against the room host.
Acquiring a borrowed handle uses exactly `context.participant`, without spawning or replacing it.
`prepare` captures posture before changing it; `release` restores only its own token and never
removes a borrowed actor. Minecraft saves the original posture while the actor is controlled.
`seatAssigned` saves the public seat request. `ExampleActorReturnToSeat` implements this example's
own return behavior within six blocks through its furniture services. Without a seat, it safely
teleports to the assigned position and stands only when the space is clear and the ground supports it.
It waits when blocked or when an existing seat cannot be used. The framework does not pull borrowed
actors back; other integrations decide how to move their own actors.

[`ExampleFabricMahjongClientExtension.kt`](src/main/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/ExampleFabricMahjongClientExtension.kt)
is declared under `mahjongcraft:client_extension`. Its factory creates an independent entity without
adding it to the world. Preview disposal discards only that independent object. The public appearance
descriptor stores capability IDs, not native objects or arbitrary NBT.

The bench reuses the chair's support and mount service. Cushions use the same mount service from
manual interaction, actor placement and their provider. No example reaches into MahjongCraft's DI,
internal room repository or private seating implementation.

Focused checks include actor authorization, token-scoped cleanup, original-pose NBT, bench connection
geometry, appearance persistence, client entrypoint isolation and actual remapped-JAR resources.
Use the [parent guide](../../README.md#example-actors-and-benches) for in-game steps and coverage limits.
