# Minecraft 1.20.1 Fabric Debug Tools

## Purpose

`minecraft-v1.20.1-fabric-debug` holds the development-only debug tools for the
[Fabric adapter](../fabric/README.md): the `/mahjongcraft debug` commands, the client-side
`/mahjongcraft_client debug` command, debug scenarios, debug rule variants, history generation, stress tests
and their translations.

Its Gradle project path is `:minecraft_v1.20.1_fabric-debug`, and its Fabric mod ID is
`mahjongcraft_debug`.

## Loading

The Fabric adapter's `runClient` and `runServer` load this module automatically; no build switch is
needed. It is never bundled into the MahjongCraft JAR and is not published, so a production installation
has no debug code at all. The build still compiles and tests it with every target build.

## Connection to the adapter

The module depends on the Fabric adapter and plugs into it through entrypoints declared in its
`fabric.mod.json`:

- `mahjongcraft:development`: the adapter calls it only in a development environment. It loads the debug
  services into the adapter's Koin before extensions register, then registers the debug commands and
  connects the adapter's development hooks: the win-celebration adjustment used by the showcase override
  and the table-end notification that clears per-table debug settings.
- `mahjongcraft:extension` and `mahjongcraft:client_extension`: the debug rule variants, scripted AI,
  preview vocabulary and the armor-stand character preview, registered like any other extension.
- `client`: registers the client-side debug command, which reads the adapter's client services from Koin
  when it runs.

The adapter never references debug classes. Debug code may use the adapter's public declarations only;
when it needs something internal, make that declaration public in the adapter instead of copying it.

## Dependency injection

The debug services are checked at build time together with the adapter's server and client graphs through
`debugKoinApplication`, which the tests also use to build the debug services.

## Stress tests

`/mahjongcraft debug stress` derives every table's match seed from one base seed and the order in which the
table was created. The start message and the report show the base seed; running again with the `seed`
option and the same other options deals every table the same seats, walls and dice, for example to look at
a table that was slow. An AI decision that times out under load falls back to a fixed command, so a replayed
match can still take a different course.

## Translations

Debug messages use their own language files under `src/main/resources/assets/mahjongcraft/lang`, kept
sorted and checked by the same verification tasks as the adapter's language files.
