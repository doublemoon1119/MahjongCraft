# Minecraft 1.20.1 Forge

## Purpose

`minecraft-v1.20.1-forge` is the Forge 1.20.1 adapter. It compiles the
[Minecraft 1.20.1 mod](../mod/README.md) sources together with its own and packages them as a Forge mod
that runs with [Kotlin for Forge](https://github.com/thedarkcolour/KotlinForForge).

Its Gradle project path is `:minecraft_v1.20.1_forge`, its code uses the
`com.doublemoon1119.mahjongcraft.platform.forge` package, and it belongs to the
`minecraft-v1.20.1-forge` target.

## Status

The server side is implemented: content registration, server events, networking, extension discovery
and the shader shadow check. The client side is not implemented yet, so the adapter is not distributed.

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
- jar-in-jar embeds only the libraries declared directly, so a library those need at run time must be declared
  as well. `verifyEmbeddedLibraries`, part of `check`, fails the build when the mod JAR does not embed, at the
  same version, every external library a development run resolves. Development runs leave out Koin's
  `stately` libraries: Koin's multiplatform metadata lists them, but its JVM classes do not use them.
- Development runs load the libraries from an unpacked copy in the `devLibraries` source set, which belongs
  to the same mod: libraries on the launcher class path cannot see the Kotlin provided by Kotlin for Forge.
- ModDevGradle does not add the dependencies of mod dependencies, so the Kotlin for Forge API used by the
  mod, `kfflib`, is a compile-only dependency; Kotlin for Forge itself provides it at run time.

## Extensions

Other mods declare extensions in their `mods.toml`, under `modproperties`, with the same names as the Fabric
entrypoints. The value is a class name or a list of class names; a Kotlin `object` supplies its instance,
other classes are created with a no-argument constructor.

```toml
[modproperties.examplemod]
"mahjongcraft:extension" = "com.example.ExampleExtension"
```

## Mixins

`mahjongcraft.forge.mixins.json` lists the Forge-only mixins. They provide the server events Forge does
not have, at the same points where Fabric API fires them: an entity added to a world, a block entity loaded
into a chunk, and a block a player has broken. ModDevGradle does not add `MixinConfigs` to the JAR manifest,
so the `jar` task adds it; production Forge ignores mixin configurations missing from the manifest.

## Running

```shell
./gradlew :minecraft_v1.20.1_forge:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-forge"
./gradlew :minecraft_v1.20.1_forge:runServer "-PmahjongcraftTarget=minecraft-v1.20.1-forge"
```

Client and dedicated-server runs use `run/client` and `run/server` under this module.
