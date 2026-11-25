# Shared Fabric Debug Sources

## Purpose

This folder holds the Fabric side of the development-only debug tools that is the same for every supported
Minecraft version: the Fabric client entrypoint and the Koin assembly of the debug services, with their tests.
It is not a Gradle project: each version's Fabric debug module, such as
[Minecraft 1.20.1 Fabric debug tools](../v1.20.1/fabric-debug/README.md) and
[Minecraft 1.21.1 Fabric debug tools](../v1.21.1/fabric-debug/README.md), compiles it together with its own
sources.

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
`debugKoinApplication`. The tests pass it to the shared assembly test of the
[shared debug tools](../mod-debug/README.md#testing).
