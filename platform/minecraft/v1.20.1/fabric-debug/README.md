# Minecraft 1.20.1 Fabric Debug Tools

## Purpose

`minecraft-v1.20.1-fabric-debug` connects the development-only debug tools to the
[Fabric adapter](../fabric/README.md). The tools themselves live in the
[Minecraft 1.20.1 mod debug tools](../mod-debug/README.md) and the
[common debug tools](../../common-debug/README.md); this module compiles the
[shared Fabric debug sources](../../fabric-debug/README.md) with the Fabric mod metadata, and compiles and
packages the other two modules' sources and resources.

Its Gradle project path is `:minecraft_v1.20.1_fabric-debug`, and its Fabric mod ID is
`mahjongcraft_debug`.

## Loading

The Fabric adapter's `runClient` and `runServer` load this module automatically; no build switch is
needed. It is never bundled into the MahjongCraft JAR and is not published, so a production installation
has no debug code at all. The build still compiles and tests it with every target build.
