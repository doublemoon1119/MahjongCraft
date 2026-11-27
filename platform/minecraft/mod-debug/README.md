# Shared Minecraft Mod Debug Tools

## Purpose

This folder holds the development-only debug tools that call Minecraft but no loader API and are the same for
every supported Minecraft version: the `/mahjongcraft debug` commands, the client-side
`/mahjongcraft_client debug` command, debug scenarios, history generation, stress tests, the automated tests,
the debug extension and the armor-stand character preview.

It is not a Gradle project: each version's mod debug module, such as the
[Minecraft 1.20.1 mod debug tools](../v1.20.1/mod-debug/README.md), compiles it together with its own sources.
The code uses the `com.doublemoon1119.mahjongcraft.platform.minecraft.mod` package, with debug code under
`debug` sub-packages so that no package overlaps with the [shared Minecraft mod sources](../mod/README.md).

## Boundaries

Loader APIs do not belong here; each version's mod debug module compiles this folder against vanilla
Minecraft only, so using one fails its build. Code that differs between Minecraft versions belongs in each
version's mod debug module. Debug code that does not need Minecraft belongs in the
[common debug tools](../common-debug/README.md), and loader entrypoints belong in the loader's debug
module, such as the [Fabric debug tools](../v1.20.1/fabric-debug/README.md) or the
[Forge debug tools](../v1.20.1/forge-debug/README.md).

Debug code may use the main mod's public declarations only; when it needs something internal, make that
declaration public in the main mod instead of copying it.

## Dependencies

Each version's mod debug module compiles this folder with the dependencies of that module: the version's mod
module, `:minecraft_common-debug` and the modules below them. Loader debug modules compile these sources
together with their own.

## Stress tests

`/mahjongcraft debug stress` derives every table's match seed from one base seed and the order in which the
table was created. The start message and the report show the base seed; running again with the `seed`
option and the same other options deals every table the same seats, walls and dice, for example to look at
a table that was slow. An AI decision that times out under load falls back to a fixed command, so a replayed
match can still take a different course.

## Testing

The tests run with each version's mod debug module, for example:

```shell
./gradlew :minecraft_v1.20.1_mod-debug:test "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

The debug language file test reads the files in the common debug tools.

`src/loaderTest/kotlin` holds `DebugDevelopmentAssemblyContract`, which checks that the debug services
connect to the main mod: the extension registration, the command tree and the development hooks. It
needs a loader's service graph, so the version's mod debug module does not compile it. Each loader debug module compiles it
with its own tests and supplies that graph.

## Automated tests

`client/debug/autotest` holds the automated client test that development runs start with
`-PmahjongcraftAutotest` (see
[Minecraft Build](../../../docs/contributing/minecraft-build.md#automated-test-runs)). Each
step implements `AutotestStep` and is listed in `autotestClientSteps`; the first step creates and enters the test
world and always runs. A step records checks and screenshots through `AutotestContext`; an exception or a failed
check fails the step without stopping the steps after it.

Automated tests run in a fixed environment and act only on explicit conditions:

- Before an action, wait for the state that decides whether it is accepted, read from the same source the
  game uses. For example, wait until the server's game can take an operation before loading a scenario, and
  for the player's decision prompt before choosing an action.
- After an action, wait for its visible result, such as the client receiving a teleport or an item, a call
  being voiced, or a presentation removing its entities.
- Do not resend an action on a timer. A rejected command or action fails the check; every wait has a time
  limit that also fails the check.
- Before a table screenshot, wait until the client holds the decision prompt the server would send the player
  now, and open the decision screen when one is pending, so every screenshot shows the same situation.
- A fixed number of game ticks is only for choosing when to take a screenshot during an animation.

Steps that need fixed situations create them instead of waiting for them by chance:

- `/mahjongcraft debug room start <seat>` starts the room's match through the normal start flow with the
  host at the given seat, so a new match always moves the player to a different seat.
- `/mahjongcraft debug history fixture <id>` records a fixed match from the common debug tools'
  `HistoryFixtures`: its first rounds deal scripted hands, walls and dice and follow scripted decisions; the
  later rounds use a fixed seed and every player discards the drawn tile and passes every call until the
  match ends. A rule whose dealer keeps the seat after a draw, such as Taiwan mahjong, cannot end a match that
  way, so its fixtures script every round. A fixture whose script cannot be followed fails and is not
  archived.
- The replay step finds the steps to show by the kind of event in the replay, not by step numbers.

Steps drive the main mod's screens only through its public entry points; the history screens offer
`HistoryScreenAutomation` for opening pages and moving through a replay.

Server commands run on the integrated server thread as the local player and return their feedback instead of
sending it to chat; feedback that arrives later, such as an asynchronous scenario load, joins the same result.

`server/game/debug/autotest` holds the dedicated server's automated test, which the development entry point
starts on a dedicated server that the `autotestServer` task launched. A dedicated server has no players, so bot players join
through the vanilla join path on an in-memory connection: they are online like real players, every packet
sent to them is dropped, and they answer keep-alive checks so the server does not disconnect them. Each bot
places a table, opens a room, switches it to its rule and fills it with AI players through the same room
service the room screen uses. The first run plays one match with automatic controls, then starts a second
match with every table's bot in the dealer's seat, so that it is the first to decide, and records every
table's progress, including each player's score, once all bots' decision times are counting down. No table
moves on after that until shutdown. The second
run compares the loaded state with that record before the bots rejoin and finish. Both runs check how long
the server takes to stop and that MahjongCraft logged no errors. The report and its retention are shared with
the client test in `debug/autotest`.

## Showcase screenshots

`client/debug/showcase` holds the run that takes the promotional screenshots when development runs start
with `-PmahjongcraftShowcase=true` (see
[Minecraft Build](../../../docs/contributing/minecraft-build.md#showcase-screenshots)). `ShowcaseScene`
builds the scene and lists the cameras; it reuses `AutotestContext` and the table helpers of the automated
client test and follows the same waiting rules. Before each screenshot it waits until the chunks around the
camera are loaded and the terrain in view has been built for a number of consecutive ticks; the fixed wait
after that only chooses when to take the picture. `debug/showcase` holds the mode switch, the result file,
`ShowcaseImages`, which crops the screenshots and builds the GitHub social preview with plain Java imaging,
and `ShowcaseSiteChooser`, which checks that the table's spot is usable.

The table always stands on the same spot of the fixed-seed world, next to the cherry grove nearest the
spawn, and the scene does not dig into the terrain. Trees in that world depend on the order chunks are
generated in, so the client first flies above the grove and loads the same area every time.
`ShowcaseSiteChooser` then checks the fixed spot: open, nearly flat ground with nothing low overhead near
the table, no hillside next to it and enough cherry trees around. `ShowcaseSunlight` checks that the
evening sun reaches the tabletop, looking from points on the tabletop towards the sun. Trees can still grow
slightly differently, so when either check fails the client leaves the world and generates it again, up to a
fixed number of times, instead of moving the table. Ground one block lower than the table is filled up to the same height, and only the
plants where the table stands are removed.

The scene puts a wooden mahjong stool on every side, a barrel with a cake next to the stool on the
player's right and two lanterns on fence posts beyond the far corners. The match starts from the server
with a fixed seat order and seed, so the player always sits on the north side and the cameras always face
the evening sun from the same direction. Vanilla does not draw the local player while the camera is on
another entity, so `ShowcaseStandIn`, a client-only player with the default Steve skin, sits on its own
client-only seat in the player's place. The table shots use the `showcase_midgame` debug scenario, a
mid-game table with full discard rivers, a riichi and a pon; the win shot uses `showcase_tsumo` and is
taken while the lightning of the win effect strikes the winning tile.

When the distant scenery camera's view of the table is blocked, it turns around the table, then moves
closer, until it sees the table; the cameras near the seat stay where they are, because the spot keeps that
area open and turning them could show the other players' tiles. The GitHub social preview darkens the top
of the picture with a fading gradient and draws a soft dark outline around the title, so the white and
green title stays readable over a bright sky or blossoms.

Showcase runs share the automated test's input and sound handling: the game ignores mouse and keyboard input
and records sounds instead of playing them.
