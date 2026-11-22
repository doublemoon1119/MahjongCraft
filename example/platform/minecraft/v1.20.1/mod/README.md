# Minecraft 1.20.1 Mod Example

This library holds the example's Minecraft 1.20.1 code that does not use a loader API: the public
game-event chat, the bench and flat-table geometry and placement rules, the table waterlogging rules,
the actor pose, and the hand-reveal chip entity and renderer.

Its Gradle project path is `:example-minecraft_v1.20.1_mod`, and its code uses the
`com.doublemoon1119.mahjongcraft.example.minecraft.mod` package.

The module compiles and tests against vanilla Minecraft only, so using a loader API fails its build.
Each loader's example adapter, such as the [Fabric 1.20.1 example](../fabric/README.md), compiles these
sources together with its own and packages them into one mod; this module's own JAR is never
distributed.

Registering blocks, entities, renderers and the creative tab, and the classes that need those
registered objects, stay in the loader adapters for now, because each loader registers content
differently.
