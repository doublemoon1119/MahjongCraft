# Minecraft 1.20.1 Forge Debug Tools

## Purpose

`minecraft-v1.20.1-forge-debug` connects the development-only debug tools to the
[Forge adapter](../forge/README.md). The tools themselves live in the
[Minecraft 1.20.1 mod debug tools](../mod-debug/README.md) and the
[common debug tools](../../common-debug/README.md); this module holds the Forge entry point, the Koin
assembly of the debug services and the Forge mod metadata, and compiles the other two modules' sources
and resources.

Its Gradle project path is `:minecraft_v1.20.1_forge-debug`, and its Forge mod ID is
`mahjongcraft_debug`.

## Loading

The Forge adapter's `runClient` and `runServer` load this module's build output as a separate mod; no
build switch is needed. It is never bundled into the MahjongCraft JAR and is not published, so a
production installation has no debug code at all. The build still compiles and tests it with every target
build.

## Connection to the adapter

The module declares its classes in `mods.toml` under `modproperties`, with the same names as the Fabric
entrypoints:

- `mahjongcraft:development`: the adapter calls it only in a development environment. It loads the debug
  services into the adapter's Koin before extensions register, then registers the debug commands and
  connects the adapter's development hooks: the win-celebration adjustment used by the showcase override
  and the table-end notification that clears per-table debug settings.
- `mahjongcraft:extension` and `mahjongcraft:client_extension`: the debug rule variants, scripted AI,
  preview vocabulary and the armor-stand character preview, registered like any other extension.

The `@Mod` entry point registers the client-side debug command on the client, which reads the adapter's
client services from Koin when it runs. The adapter never references debug classes.

## Dependency injection

The debug services are checked at build time together with the adapter's server and client graphs through
`debugKoinApplication`. The tests pass it to the shared assembly test in the
[Minecraft 1.20.1 mod debug tools](../mod-debug/README.md#testing).
