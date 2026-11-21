# Minecraft 1.20.1 Mod

## Purpose

`minecraft-v1.20.1-mod` contains the Minecraft 1.20.1 code and resources shared by every loader that
targets that game version: code that calls Minecraft 1.20.1 but no loader API.

Its Gradle project path is `:minecraft_v1.20.1_mod`, and its code uses the
`com.doublemoon1119.mahjongcraft.platform.minecraft.mod` package.

## Responsibilities

- Hold loader-independent Minecraft 1.20.1 code, such as entity save data.
- Store version-specific data formats, currently including Minecraft 1.20.1 recipe JSON.

## Boundaries

Loader APIs do not belong here; this module compiles against vanilla Minecraft only, so using one
fails its build. Logic that does not need Minecraft classes belongs in
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

The recipe resources are also verified by the Minecraft common tests. Checking this module alone
is not a substitute for the [complete target build](../../README.md), which compiles its code into
each loader.
