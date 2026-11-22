# Minecraft 1.20.1 Forge

## Purpose

`minecraft-v1.20.1-forge` is the Forge 1.20.1 adapter. It compiles the
[Minecraft 1.20.1 mod](../mod/README.md) sources together with its own and packages them as a Forge mod
that runs with [Kotlin for Forge](https://github.com/thedarkcolour/KotlinForForge).

Its Gradle project path is `:minecraft_v1.20.1_forge`, its code uses the
`com.doublemoon1119.mahjongcraft.platform.forge` package, and it belongs to the
`minecraft-v1.20.1-forge` target.

## Status

The adapter is a skeleton: Forge loads the mod and a development run reaches a world, but the loader
interfaces are not implemented yet, so no MahjongCraft content is registered. It is not distributed.

## Dependencies

- Kotlin for Forge provides the Kotlin standard library, kotlin-reflect, kotlinx-coroutines and
  kotlinx-serialization at run time, so the mod does not bundle them.
- The other libraries, including the core modules, are embedded with jar-in-jar. Kotlin multiplatform
  libraries are declared with their `-jvm` coordinates, because ModDevGradle cannot identify the
  redirected variant. Embedded JARs lose their Java module descriptors, so a library such as
  kotlinx-datetime does not require a `kotlin.stdlib` module that Kotlin for Forge does not name.
- Forge refuses two JARs that contain the same package. `verifyNoSplitPackages`, part of `check`, fails the
  build when the mod JAR and an embedded JAR share one; the compile-time Koin hints are left out of the mod
  JAR for this reason.
- Development runs load the libraries from an unpacked copy in the `devLibraries` source set, which belongs
  to the same mod: libraries on the launcher class path cannot see the Kotlin provided by Kotlin for Forge.

## Mixins

ModDevGradle does not add `MixinConfigs` to the JAR manifest. When the adapter gains mixins, the `jar` task
must add that attribute, or production Forge ignores the mixin configuration.

## Running

```shell
./gradlew :minecraft_v1.20.1_forge:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-forge"
./gradlew :minecraft_v1.20.1_forge:runServer "-PmahjongcraftTarget=minecraft-v1.20.1-forge"
```

Client and dedicated-server runs use `run/client` and `run/server` under this module.
