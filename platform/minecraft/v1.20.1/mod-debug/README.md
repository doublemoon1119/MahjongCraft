# Minecraft 1.20.1 Mod Debug Tools

## Purpose

`minecraft-v1.20.1-mod-debug` holds the development-only debug tools that call Minecraft 1.20.1 but no
loader API: the `/mahjongcraft debug` commands, the client-side `/mahjongcraft_client debug` command,
debug scenarios, history generation, stress tests, the debug extension and the armor-stand character
preview.

Its Gradle project path is `:minecraft_v1.20.1_mod-debug`, and its code uses the
`com.doublemoon1119.mahjongcraft.platform.minecraft.mod` package, with debug code under `debug`
sub-packages so that no package overlaps with the [Minecraft 1.20.1 mod](../mod/README.md).

## Boundaries

Loader APIs do not belong here; this module compiles against vanilla Minecraft only, so using one
fails its build. Debug code that does not need Minecraft belongs in the
[common debug tools](../../common-debug/README.md), and loader entrypoints belong in the loader's debug
module, such as [Fabric debug tools](../fabric-debug/README.md).

Debug code may use the main mod's public declarations only; when it needs something internal, make that
declaration public in the main mod instead of copying it.

## Dependencies

The module depends on `:minecraft_v1.20.1_mod`, `:minecraft_common-debug` and the modules below them,
and reads the same access transformer as the mod module. Loader debug modules compile its sources
together with their own; its own JAR uses Mojang names and is never distributed.

## Stress tests

`/mahjongcraft debug stress` derives every table's match seed from one base seed and the order in which the
table was created. The start message and the report show the base seed; running again with the `seed`
option and the same other options deals every table the same seats, walls and dice, for example to look at
a table that was slow. An AI decision that times out under load falls back to a fixed command, so a replayed
match can still take a different course.

## Testing

Run this module's tests against vanilla Minecraft from the repository root:

```shell
./gradlew :minecraft_v1.20.1_mod-debug:test "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

The debug language file test reads the files in the common debug tools.
