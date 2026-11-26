# Minecraft 1.20.1 Mod

## Purpose

`minecraft-v1.20.1-mod` compiles the [shared Minecraft mod sources](../../mod/README.md) against Minecraft
1.20.1, together with the 1.20.1 adapters and the files that differ by Minecraft version. Every loader that
targets Minecraft 1.20.1 compiles both.

Its Gradle project path is `:minecraft_v1.20.1_mod`, and its code uses the
`com.doublemoon1119.mahjongcraft.platform.minecraft.mod` package.

## Responsibilities

- Provide the 1.20.1 side of every adapter the shared code calls: the `vanilla` package and the version base
  classes, such as the entity, block, screen, button, saved data and item bases.
- Hold the files that each Minecraft version keeps its own copy of, such as the entity type factory, the
  achievement trigger and the translucent vertex consumer.
- Store version-specific data formats: recipes, advancements, loot tables and item tags, and the access
  transformer.
- Hold the client mixins every loader applies, in the `mixin.client` package. Each loader lists them in its
  own mixin configuration; this module compiles them against the Mixin and MixinExtras annotations only.
  A point in the frame that no loader exposes, such as the end of world rendering, is published through
  a loader-neutral hook in the `loader` package.

## Boundaries

Loader APIs do not belong here; this module compiles against vanilla Minecraft only, so using one
fails its build. Code that is the same for every Minecraft version belongs in the
[shared sources](../../mod/README.md). Logic that does not need Minecraft classes belongs in
[minecraft-common](../../common/README.md), as do assets that remain compatible across Minecraft
versions.

## Dependencies

The module uses the target-specific Java toolchain and release configured by the Minecraft mod
convention, and the ModDevGradle legacy plugin in vanilla mode for Minecraft itself. Loader modules
compile its sources and package its resources together with their own; its own JAR uses Mojang names
and is never distributed. See [Minecraft Build](../../../../docs/contributing/minecraft-build.md#version-mod-modules).

## Testing

Run this module's tests against vanilla Minecraft from the repository root:

```shell
./gradlew :minecraft_v1.20.1_mod:test "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```

The run also covers the shared tests and this version's data pack files. Checking this module alone
is not a substitute for the [complete target build](../../README.md), which compiles its code into
each loader.
