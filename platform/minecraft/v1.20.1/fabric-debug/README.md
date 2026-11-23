# Minecraft 1.20.1 Fabric Debug Tools

## Purpose

`minecraft-v1.20.1-fabric-debug` connects the development-only debug tools to the
[Fabric adapter](../fabric/README.md). The tools themselves live in the
[Minecraft 1.20.1 mod debug tools](../mod-debug/README.md) and the
[common debug tools](../../common-debug/README.md); this module holds the Fabric client entrypoint, the
Koin assembly of the debug services and the Fabric mod metadata, and compiles and packages the other two
modules' sources and resources.

Its Gradle project path is `:minecraft_v1.20.1_fabric-debug`, and its Fabric mod ID is
`mahjongcraft_debug`.

## Loading

The Fabric adapter's `runClient` and `runServer` load this module automatically; no build switch is
needed. It is never bundled into the MahjongCraft JAR and is not published, so a production installation
has no debug code at all. The build still compiles and tests it with every target build.

## Connection to the adapter

The module plugs into the adapter through entrypoints declared in its `fabric.mod.json`:

- `mahjongcraft:development`: the adapter calls it only in a development environment. It loads the debug
  services into the adapter's Koin before extensions register, then registers the debug commands and
  connects the adapter's development hooks: the win-celebration adjustment used by the showcase override
  and the table-end notification that clears per-table debug settings.
- `mahjongcraft:extension` and `mahjongcraft:client_extension`: the debug rule variants, scripted AI,
  preview vocabulary and the armor-stand character preview, registered like any other extension.
- `client`: registers the client-side debug command, which reads the adapter's client services from Koin
  when it runs.

The adapter never references debug classes.

## Dependency injection

The debug services are checked at build time together with the adapter's server and client graphs through
`debugKoinApplication`. The tests pass it to the shared assembly test in the
[Minecraft 1.20.1 mod debug tools](../mod-debug/README.md#testing).
