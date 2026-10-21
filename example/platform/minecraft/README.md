# Minecraft Extension Example

The example is opt-in and is not included in normal builds or normal MahjongCraft runs.
It uses the same compiler, Java and Minecraft version policies as the main project.

## Structure

- [Platform-independent extension](../../mahjong-extension/README.md): common Kotlin code.
- [Minecraft common](common/README.md): shared presentation and resources.
- [Fabric 1.20.1](v1.20.1/fabric/README.md): the example mod and native entrypoints.

The common extension registers the pairing rule, custom tile and Tsumogiri strategy.
Minecraft presentation adds room settings, declaration and exchange prompts, settlement text,
and the inverted one-character tile's resources in four languages.
Native furniture demonstrates both block-backed and entity-backed seats. Public game events are
printed to online administrators' chat so their supplied fields can be inspected in game.

## Build and Run

Run these commands from the repository root. The flag accepts only `true` or `false`.
Omitting it is equivalent to `false`; it is not saved by `switchTarget`.

```shell
./gradlew build -PmahjongcraftTarget=minecraft-v1.20.1-fabric -PmahjongcraftExample=true
./gradlew :example-minecraft_v1.20.1_fabric:runClient -PmahjongcraftTarget=minecraft-v1.20.1-fabric -PmahjongcraftExample=true
./gradlew :example-minecraft_v1.20.1_fabric:runServer -PmahjongcraftTarget=minecraft-v1.20.1-fabric -PmahjongcraftExample=true
```

Both example runs load MahjongCraft and the example as separate mods.
Their worlds, configuration and logs stay under this example loader's `run/client` and `run/server`.
They never reuse the main loader's development worlds.
The main loader's own run tasks do not gain the example when the flag is enabled.

Use disposable example worlds. Do not use worlds containing example data without the matching mod.
Later integration tests will verify missing-extension handling; this skeleton does not establish that coverage.

## Packaging

The example JAR contains its loader entrypoints, both example libraries and its own resources.
The main mod remains a separate required JAR. Neither mod includes the other.
The example uses its own metadata and archive name; the icon currently reuses the project logo
under the example's own resource namespace.

No example module starts Koin. Extension registration goes through MahjongCraft's public entrypoint.
Successful compilation alone is not evidence that a rule or another extension capability works.

## Furniture and Event Checks

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
