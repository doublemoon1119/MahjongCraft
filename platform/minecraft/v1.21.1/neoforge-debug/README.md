# Minecraft 1.21.1 NeoForge Debug Tools

## Purpose

`minecraft-v1.21.1-neoforge-debug` connects the development-only debug tools to the
[NeoForge adapter](../neoforge/README.md). The tools themselves live in the
[Minecraft 1.21.1 mod debug tools](../mod-debug/README.md) and the
[common debug tools](../../common-debug/README.md); this module holds the NeoForge entry point, the Koin
assembly of the debug services and the NeoForge mod metadata, and compiles the other two modules' sources
and resources.

Its Gradle project path is `:minecraft_v1.21.1_neoforge-debug`, and its NeoForge mod ID is
`mahjongcraft_debug`.

## Loading

The NeoForge adapter's development runs load this module's build output as a separate mod; no build switch
is needed. It is never bundled into the MahjongCraft JAR and is not published.

## Connection to the adapter

The module declares its classes in `neoforge.mods.toml` under `modproperties`, with the same names as the
Fabric entrypoints: `mahjongcraft:development` for the development services and commands, and
`mahjongcraft:extension` and `mahjongcraft:client_extension` for the debug extensions. The adapter never
references debug classes.

The metadata also declares `mahjongcraft.debug.mixins.json`, the client mixins the automated client test uses,
for example to ignore player input and to record sound requests. The `@Mod` entry point registers the
client-side debug command and, in an automated test run, the client test.

## Dependency injection

The debug services are checked at build time together with the adapter's server and client graphs through
`debugKoinApplication`. The tests pass it to the shared assembly test in the
[Minecraft 1.21.1 mod debug tools](../mod-debug/README.md#testing).
