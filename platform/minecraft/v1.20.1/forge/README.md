# Minecraft 1.20.1 Forge

## Purpose

`minecraft-v1.20.1-forge` is the Forge 1.20.1 adapter. It compiles the
[Minecraft 1.20.1 mod](../mod/README.md) sources together with its own and packages them as a Forge mod
that runs with [Kotlin for Forge](https://github.com/thedarkcolour/KotlinForForge).

Its Gradle project path is `:minecraft_v1.20.1_forge`, its code uses the
`com.doublemoon1119.mahjongcraft.platform.forge` package, and it belongs to the
`minecraft-v1.20.1-forge` target.

## Status

The server and client sides are implemented, including the config screen opened from Forge's mod list,
and development runs load the [debug tools](../forge-debug/README.md). The mod JAR is distributable: CI
collects the JAR converted to Forge's run-time names by `reobfJar`, not the development JAR.

The Mahjong tile recipe uses the `mahjongcraft:dyes` item tag. Each loader defines it: this module combines
the vanilla dyes with Forge's `forge:dyes`, so dyes from other mods work as well.

The shared client startup reads registered items and entities, which Forge writes only after the mod is
constructed. The client therefore starts at the first client registration event, when the registries are
complete and key mappings, entity renderers, item colors, reload listeners and GUI overlays are not
registered yet.

Forge draws the in-game screen with its own `ForgeGui` overlays instead of vanilla `Gui.render`, so the
shared `GuiMixin` never runs on Forge. The HUD drawn before the chat, such as the compact countdown and the
hand analysis, is a GUI overlay registered below Forge's chat panel instead.

## Dependencies

- Kotlin for Forge provides the Kotlin standard library, kotlin-reflect, kotlinx-coroutines and
  kotlinx-serialization at run time, so the mod does not bundle them.
- The other libraries in the shared bundled library list, including the core modules, are embedded with
  jar-in-jar. Embedded external libraries lose their Java module descriptors, so a library such as
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

`mahjongcraft.mixins.json` lists the mixins shared with Fabric, which live in the
[Minecraft 1.20.1 mod](../mod/README.md) module. `mahjongcraft.forge.mixins.json` lists the Forge-only
mixins:

- The server events Forge does not have: after vanilla starts tracking an entity added to a world, after a
  chunk records a block entity, and after a block a player breaks is actually removed.
- The Mahjong tile item supplies its programmatic renderer, because Forge asks only the item's own class.
- Model loading asks the registered model loadings first, so tile variants without a model file still load.

The shared mixins use MixinExtras, which Forge 1.20.1 does not ship; the mod embeds it with jar-in-jar.
ModDevGradle does not add `MixinConfigs` to the JAR manifest, so the `jar` task adds it; production Forge
ignores mixin configurations missing from the manifest.

## Deprecation Warnings

Forge marks several vanilla APIs as deprecated to steer mods toward Forge's own, such as the
`ResourceLocation` constructors and the `BuiltInRegistries` fields. The code shared with Fabric must use
the vanilla APIs, and compiling it against vanilla Minecraft reports no deprecation. This module's Kotlin
compilation therefore disables the deprecation warnings; real deprecations in the shared code are still
reported by the [Minecraft 1.20.1 mod](../mod/README.md) and Fabric builds.

The setting also hides deprecated Forge APIs used by this module's own code. When changing that code,
compile once without the `hideForgeDeprecationsOfVanillaApis()` call in `build.gradle.kts` and check the
warnings for files under this module. The Forge debug tools and the Forge example use the same setting.

## Running

```shell
./gradlew :minecraft_v1.20.1_forge:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-forge"
./gradlew :minecraft_v1.20.1_forge:runServer "-PmahjongcraftTarget=minecraft-v1.20.1-forge"
```

Development runs also load the [debug tools module](../forge-debug/README.md), which provides the
`/mahjongcraft debug` commands; the packaged mod does not contain it.

Compatibility checks can load extra mods into development runs with `-PmahjongcraftDevMods`
(see [Minecraft Build](../../../../docs/contributing/minecraft-build.md#development-runtime-mods)). This module and the
Forge example declare the same groups:

- `shaders`: Oculus and the Embeddium it requires, for checking rendering with and without a shader pack. Shader
  packs are not part of the repository; put one in `run/client/shaderpacks` and select it in the game's video
  settings.
- `modmenu`: empty, so the switch also works when Fabric is selected together; Forge's own mod list already
  has the settings button that opens the client settings.

```shell
./gradlew :minecraft_v1.20.1_forge:runClient "-PmahjongcraftTarget=minecraft-v1.20.1-forge" "-PmahjongcraftDevMods=shaders"
```

Client and dedicated-server runs use `run/client` and `run/server` under this module.
