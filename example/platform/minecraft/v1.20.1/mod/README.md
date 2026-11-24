# Minecraft 1.20.1 Mod Example

This library holds the example's Minecraft 1.20.1 code that does not use a loader API: the extension
entry classes, the furniture, tables, actors and hand-reveal chip with their entities, items and
renderers, the public game-event chat, and the placement, geometry and waterlogging rules.

Its Gradle project path is `:example-minecraft_v1.20.1_mod`, and its code uses the
`com.doublemoon1119.mahjongcraft.example.minecraft.mod` package.

The module compiles and tests against vanilla Minecraft only, so using a loader API fails its build.
Each loader's example adapter, the [Fabric 1.20.1 example](../fabric/README.md) and the
[Forge 1.20.1 example](../forge/README.md), compiles these sources together with its own and
packages them into one mod; this module's own JAR is never distributed.

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
same, such as `useWithoutItem`, `readData` and `writeData`. Each Minecraft version keeps its own copy of
this package with the same names, so the rest of the example does not change between versions. The
example does not use MahjongCraft's internal adapters; a mod that supports several Minecraft versions
keeps its own in the same way.

To see what connects the example to MahjongCraft, start with
[`ExampleNativeMahjongExtension.kt`](src/main/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/mod/ExampleNativeMahjongExtension.kt).
It registers the seats, table props, seat placements and actors through MahjongCraft's public
registrars, and it is the class each loader adapter declares.

The module compiles with MahjongCraft's access transformer. The example always runs with
MahjongCraft, which opens the same vanilla classes and members at run time on every loader.
