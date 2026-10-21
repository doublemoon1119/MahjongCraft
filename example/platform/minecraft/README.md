# Minecraft Extension Example

The example is opt-in and is not included in normal builds or normal MahjongCraft runs.
It uses the same compiler, Java and Minecraft version policies as the main project.

## Structure

- [Platform-independent extension](../../mahjong-extension/README.md): common Kotlin code.
- [Minecraft common](common/README.md): shared presentation and resources.
- Version/loader adapters: for example, the currently available
  [Fabric 1.20.1 adapter](v1.20.1/fabric/README.md) supplies native entrypoints and furniture.

The common extension registers the pairing rule, custom tile and Tsumogiri strategy.
Minecraft presentation adds room settings, declaration and exchange prompts, settlement text,
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

## Remaining Manual Checks

The following short integration checks are still pending:

- Enable the two-tile exchange, select multiple tiles, submit from every player, and confirm the
  simultaneous exchange leaves no duplicate, stale or replayed tile before normal draw and discard.
- Run a short two-player facing game and one multi-roll opening check if those exact combinations have
  not already been recorded as accepted.
- Run one short combination for each of these pairings: example rule with bundled AI, bundled rule with
  example AI, and example rule with example AI. A complete match or stress run is not required.

## Furniture and Event Checks

The behavior below describes the current Fabric 1.20.1 implementation. Use it as a reference when
adding another version/loader adapter, and verify that adapter separately.

The **MahjongCraft Example** creative tab contains the chair and placeable cushion, in that order.
Place either at a standard table seat. Right-click to sit; game-start seating uses the public seat
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

The example demonstrates usable seat APIs; it does not establish binary compatibility for them.
The seating ABI baseline follows the full example acceptance step.
