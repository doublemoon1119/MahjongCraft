# Minecraft Extension Example

The example is opt-in and is not included in normal builds or normal MahjongCraft runs.
It uses the same compiler, Java and Minecraft version policies as the main project.

## Structure

- [Platform-independent extension](../../mahjong-extension/README.md): common Kotlin code.
- [Minecraft common](common/README.md): shared presentation and resources, with a
  [registration map](common/README.md#registration-map) for extension authors.
- Version/loader adapters: for example, the currently available
  [Fabric 1.20.1 adapter](v1.20.1/fabric/README.md) supplies native entrypoints and furniture.

The common extension registers the pairing rule, custom tile and Tsumogiri strategy.
Minecraft presentation adds room settings, hand-reveal and exchange prompts, settlement text,
and the inverted one-character tile's resources in four languages.
Native furniture demonstrates both block-backed and entity-backed seats. Public game events are
printed to online administrators' chat so their supplied fields can be inspected in game.

## Build and Run

Choose a supported target and its example loader project. The commands below use the currently
available Fabric 1.20.1 adapter as an example; other adapters have their own target and task paths.
Run them from the repository root. The flag accepts only `true` or `false`.
Omitting it is equivalent to `false`; it is not saved by `switchTarget`.

```shell
./gradlew build -PmahjongcraftTarget=minecraft-v1.20.1-fabric -PmahjongcraftExample=true
./gradlew :example-minecraft_v1.20.1_fabric:runClient -PmahjongcraftTarget=minecraft-v1.20.1-fabric -PmahjongcraftExample=true
./gradlew :example-minecraft_v1.20.1_fabric:runServer -PmahjongcraftTarget=minecraft-v1.20.1-fabric -PmahjongcraftExample=true
```

In this Fabric 1.20.1 example, both runs load MahjongCraft and the example as separate mods.
Their worlds, configuration and logs stay under the adapter directory
`example/platform/minecraft/v1.20.1/fabric/`, in `run/client` and `run/server`.
Example runs must remain separate from the product loader's development worlds and classpath.
Enabling the example flag does not add the example to the product loader's own run tasks.

## Safe Recovery Check

Use a disposable world and keep a full backup after a normal exit. Do not use the main development
world for removal tests.

The missing-extension recovery scenario is still a manual check, not an automated guarantee:

1. Start with the same product and example versions. Create an active pairing match on the bundled
   table and stools, and record its venue ID, match ID, settings and scores.
2. Stop normally, copy the whole test instance, then move only the example JAR out of that copy.
   Use a packaged, isolated instance; turning the Gradle example flag off is not the same test.
3. Open the copied world. It should load, warn administrators that the example extension is missing,
   and disable MahjongCraft actions that would write authoritative match data. It must not replace the
   authoritative state with an empty file. Do not break or remove the fixture table while the example is
   absent; that would test missing native content rather than extension recovery.
4. Stop and compare the SHA-256 of `data/mahjongcraft_authoritative_state.dat` with the backup.
   Restore the same example JAR in the same instance and verify that the original venue and match IDs,
   settings and scores return, and that play can continue. Keep the backup and logs if they do not.

The table-location index can be rebuilt from the world, so it is not evidence that authoritative data
was recovered. Example blocks, entities and items may be removed by Minecraft when their mod is absent;
reinstalling the JAR does not promise to restore those native objects. Test example furniture separately.

## Packaging

The example JAR contains its loader entrypoints, both example libraries and its own resources.
The main mod remains a separate required JAR. Neither mod includes the other.
The example uses its own metadata and archive name; the icon currently reuses the project logo
under the example's own resource namespace.

No example module starts Koin. Extension registration goes through MahjongCraft's public entrypoint.
Successful compilation alone is not evidence that a rule or another extension capability works.

For the current Fabric 1.20.1 adapter, the loader-specific entry and observer test are
[`ExampleMod.kt`](v1.20.1/fabric/src/main/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/ExampleMod.kt)
and [`ExampleGameEventChatTest.kt`](v1.20.1/fabric/src/test/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/event/ExampleGameEventChatTest.kt).
The shared Minecraft registration and its test are
[`ExampleMinecraftMahjongExtension.kt`](common/src/commonMain/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/ExampleMinecraftMahjongExtension.kt)
and [`ExampleMinecraftRegistrationTest.kt`](common/src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/ExampleMinecraftRegistrationTest.kt).

The Fabric 1.20.1 packaged dedicated-server startup smoke test has already passed.
It does not establish support for other adapters or replace the manual
checks above or prove every world interaction.

## Accepted Game Checks

The following integration scenarios have been accepted in game on the current Fabric 1.20.1 adapter.
They do not need repeating solely for documentation or ABI baseline changes:

- Enable the two-tile exchange, select multiple tiles, submit from every player, and confirm the
  simultaneous exchange leaves no duplicate, stale or replayed tile before normal draw and discard.
- Check two-player facing seats and the multi-roll opening.
- Run one short combination for each of these pairings: example rule with bundled AI, bundled rule with
  example AI, and example rule with example AI. A complete match or stress run is not required.

The separate missing-extension recovery procedure above has not been verified at world level.
It is not a reason to repeat these accepted gameplay checks.

## Furniture and Event Checks

The behavior below describes the current Fabric 1.20.1 implementation. Use it as a reference when
adding another version/loader adapter, and verify that adapter separately.

The **MahjongCraft Example** creative tab contains the chair, placeable cushion, bench, actor item and three tables.
Place a chair, bench or cushion at a standard table seat. Right-click to sit; game-start seating uses the public seat
provider API. An occupied seat never ejects its rider. Leaving a chair removes its temporary invisible
support; leaving a cushion keeps the cushion in the world. Left-click an empty cushion to remove it.
Survival drops one item; creative mode does not add another item. Modification permissions and the
world's entity-drop rule still apply. An occupied cushion cannot be removed by attacking it.

Cushions are horizontally centered on the clicked block and sit at the actual top-face height,
including ordinary slabs and thin surfaces. Placement rotates in 90-degree steps. Removing their
support releases any rider and removes the cushion. This is a small placement example, not a complete
backport of every vanilla cushion interaction or special block shape.

At match start, round settlement and match end, online players with permission level 2 receive
one translated summary line in chat, styled as an aqua bracketed label without an explicit hover hint.
Hovering over the label shows the complete public event fields.
Single-player testing therefore needs operator permission, such as
an example world with cheats enabled. Non-administrators do not receive this diagnostic output.
Missing table locations and offline player names still produce output, using an unknown location or
the player's UUID. The observer reads public events, not hidden hands or authoritative repositories.

These notifications are live observations, not a replay or a reliable reward system. Debug-affected
matches stop notifying, events are not replayed after registration, and old-session events are
discarded. A start is not guaranteed to have a matching end. An aborted match is shown separately
from a normally completed match. Do not use debug commands for the ordinary event-delivery check.

The example demonstrates usable seat APIs. The public API ABI check records the seating
contracts for the adapter being verified; it does not promise compatibility with another version
or loader, and it does not cover unrelated rule, AI or appearance types.

### Place a seat for a standing bot

Hold an example chair, bench or cushion and right-click a standing Mahjong Bot. The item is placed at that
bot's assigned seat, then the bot sits down. The built-in stools use the same mechanism.

- Survival placement consumes one item; creative placement does not.
- Blocked, occupied or protected positions reject placement without consuming the item.
- If the seat was placed but the bot could not sit, the seat remains and a message explains the result.
- Other entities and unrelated items retain their normal interaction. Sneaking skips this shortcut.

`ExampleSeatPlacements` demonstrates `FabricSeatPlacementProvider` for block furniture and an entity
item. The cushion shares its normal placement function with this shortcut. Its existing-seat provider
is registered separately and remains responsible for validating the actual sitting position.

## Example Actors and Benches

The current Fabric 1.20.1 adapter demonstrates a mod-owned actor using the vanilla armor-stand model.
It does not turn ordinary armor stands into Mahjong players or use the product's debug adapter.
Other version/loader adapters need their own native registration and checks.

1. Take **Example Actor** and a chair, bench or cushion from the example creative tab.
2. Create a room at a table. Use the actor item on an empty example seat at any standard table side.
   Using it on ordinary ground creates a standing actor; it must sit at a table to join.
3. The seated actor joins automatically. Only rooms hosted by the player who placed it are allowed.
   This is the example's permission policy, not a restriction on all third-party integrations.
4. Add other players or bots and start. MahjongCraft assigns the final seats; the actor is the same
   world entity, not a replacement spawned for the match. A name tag changes its name, not its UUID
   or placement ownership. Ordinary bots still start at AI 1.
5. Kicking it uses the room's temporary exclusions. Remaining seated or reconnecting does not clear
   an exclusion; the host must allow it again. Releasing an actor does not delete it.

The placer can left-click an actor to collect it when it is not under match control. Protection,
creative mode and entity-drop rules apply. Equipment editing is disabled in this small example;
it is not a general NPC or pathfinding system.

Each bench block provides one seat. Same-height neighbors connect only along the same facing's left
and right sides. Extending the row or removing another block keeps the seated member's identity.
Breaking their own block releases only that seat. Unloaded neighbors are never force-loaded; a local
scheduled check refreshes each block's two connections when chunks become available again.
The invisible support follows vanilla passenger saving, so an actor is not lost with an unsaved mount.

Breaking a seat keeps the ordinary safe dismount. During a match, the actor keeps the seat request
received through the public `seatAssigned` callback. This example checks once per second and returns
to an empty, correctly facing example chair, bench or cushion within six blocks. If there is no seat,
it teleports to the assigned position and stands, but only when its full body has room and a block
supports its feet. Occupied, blocked or incorrectly facing seats do not fall back to standing.
A changed seat request releases its previous example furniture before trying the new seat;
repeating an unchanged request does not unseat it. This is the example's own simple behavior, not
movement imposed on other integrations. Other integrations can walk back, teleport or stay put.
Holding a seat item and right-clicking a controlled actor is a separate, explicit placement-and-seating
interaction; use it to place a seat when the standing actor occupies the placement space.

For an existing actor registered by an integration, such as a third-party NPC,
`[ai-player].missing-policy` in the server configuration defaults to
`abort_match`; `continue_match` keeps the same AI playing without a replacement world actor.
An actor standing nearby is still present. A dead, removed, unloaded or distant actor is missing
only while the table is active; shutdown and table unloading do not trigger the policy.
Before a match starts, missing actors leave the waiting room without a reconnect timeout.
MahjongCraft's own spawned bots are recreated by its character manager and do not use this policy.

### Coverage and limits

| Capability | What this example checks |
| --- | --- |
| Existing world participant | Dedicated actor type, placer authorization and borrowed handle |
| World and room appearance | Authorized live entity, otherwise an independent client preview |
| FACE and history | Preview-generated portrait, explicit FACE-only description, saved appearance round trip |
| Owned proxy | Registered unsaved proxy factory and ownership adapter; no room skin picker |
| Seat variety | Block chair, local-connection bench and persistent cushion |
| Cleanup | Token-scoped restoration, repeated release and partial preparation failure |

Registration and focused tests are automated. Native interaction, appearance, exclusions and reloads
still need the in-game checks for the adapter being tested. Registering the owned and FACE-only test
descriptions does not make them selectable in a normal room or prove their full in-game lifecycle.
Removing the example mod can remove its native actors and furniture; reinstalling it does not restore
native content that Minecraft already discarded. Use a disposable world for missing-mod checks.

## Bonus tiles and table props

The pairing rule saves `m - 1` bonus ranks when a round starts, where `m` is the number of ranks
selected for its wall. Two ranks produce one displayed bonus tile; eight ranks produce seven.
The win panel shows all of that round's bonus faces, the bonus points earned, and the winner's
total score. Per-player payment details remain available in history.
Settlement details store faces directly through `WinSettlementDetailValue.TileFaces`;
these display tiles do not borrow IDs from the wall or any player's hand. The resolver turns each
tile value into a public face with the `publicFace` function it receives, so a face keeps only the
appearance and features every observer may see. History keeps the same faces, including the
inverted one-man tile.
These are public display copies: no tile is removed from the wall and none acts as a wildcard.
Bonus tiles face the current dealer, using the actual standard seat layout, and rotate with the table.
Each different matching rank in a self-draw adds one point to **each opponent's payment**.
Two matching copies of the same rank count once. The win panel lists the bonus points separately from the total score.

The shared [prop describer](common/src/commonMain/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/presentation/ExamplePairingTableProps.kt)
reads the saved selection. It also requests one custom gold hand-reveal token per revealed player.
Both kinds use the same central layout. The public tile kind comes from MahjongCraft; the token's
native kind, entity and renderer belong entirely to the example adapter. For instance, the current
Fabric adapter registers them in
[`ExampleFabricMahjongExtension`](v1.20.1/fabric/src/main/kotlin/com/doublemoon1119/mahjongcraft/example/fabric/ExampleFabricMahjongExtension.kt).

After building, check the following in a disposable example world:

1. Start with two selected ranks, then with all eight. Count the displayed bonus tiles.
2. Check that the bonus tiles face the dealer, including after the next round changes dealers.
   Reveal several hands and confirm the gold tokens do not overlap tiles or one another.
3. Watch the multi-roll opening: central props appear after the dice have finished.
4. Self-draw and compare the displayed bonus ranks with the extra points. Repeated copies count once.
5. Leave and reopen an active match. Its bonus ranks and scores must stay the same.
6. Finish a round or remove the table. Its old props must not remain or reappear later.

These are new prop checks; their game acceptance is pending. The previously accepted seating,
exchange, opening and event checks above remain separate.
