# Shared Minecraft Mod Example

This folder holds the example's Minecraft code that does not use a loader API and is the same for every
supported Minecraft version: the extension entry classes, the furniture, tables, actors and hand-reveal chip
with their entities, items and renderers, the public game-event chat, and the placement, geometry and
waterlogging rules. The code uses the `com.doublemoon1119.mahjongcraft.example.minecraft.mod` package.

It is not a Gradle project: each version's example mod module, such as the
[Minecraft 1.20.1 mod example](../v1.20.1/mod/README.md), compiles it together with its own sources against
vanilla Minecraft only, so using a loader API fails its build. Each loader's example adapter, such as the
[Fabric 1.20.1 example](../v1.20.1/fabric/README.md) and the [Forge 1.20.1 example](../v1.20.1/forge/README.md),
compiles these sources together with its own and packages them into one mod.

It also lists what the example registers: blocks, items, entity types, the table block entity type,
the creative tab and the entity renderers. Each loader registers content differently, so the list is
written against two small interfaces, `ExampleRegistrar` and `ExampleRendererRegistrar`, and each
loader's example adapter implements them. Objects created during registration become available only
after the adapter has written them to the registry.

These two interfaces belong to the example, not to MahjongCraft. They exist only so that the Fabric
and Forge examples share one list of content. A mod for a single loader registers its content the
usual way for that loader, such as Fabric's `Registry.register` or Forge's `DeferredRegister`.

Calls whose API differs between Minecraft versions go through the example's own `vanilla` package:
small functions such as `resourceId` and `copyBlockProperties`, and base classes for blocks, entities,
block entities and tooltip items that turn the version's own override points into ones that stay the
same, such as `useWithoutItem`, `readData` and `writeData`. Each Minecraft version's example mod module keeps its own
copy of this package with the same names, so the code in this folder does not change between versions. The
example does not use MahjongCraft's internal adapters; a mod that supports several Minecraft versions
keeps its own in the same way.

To see what connects the example to MahjongCraft, start with
[`ExampleNativeMahjongExtension.kt`](src/main/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/mod/ExampleNativeMahjongExtension.kt).
It registers the seats, table props, seat placements and actors through MahjongCraft's public
registrars, and it is the class each loader adapter declares.

Each version's example mod module compiles this code with MahjongCraft's access transformer. The example
always runs with MahjongCraft, which opens the same vanilla classes and members at run time on every loader.
