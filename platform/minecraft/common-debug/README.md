# Minecraft Common Debug Tools

## Purpose

`minecraft-common-debug` holds the parts of the development-only debug tools that do not call
Minecraft: debug rule variants, debug scenario data, stress test statistics and options, history
generation settings, and the debug translations and textures.

Its Gradle project path is `:minecraft_common-debug`, and its code uses the
`com.doublemoon1119.mahjongcraft.platform.minecraft` package, with debug code under `debug`
sub-packages such as `server.game.debug.stress` and `config.debug`.

## Boundaries

Code here may not use Minecraft or any loader. Debug code that calls Minecraft belongs in the
[shared Minecraft mod debug tools](../mod-debug/README.md), or in a version's mod debug module when it differs
between Minecraft versions, and loader entrypoints belong in the loader's debug module.

Its packages do not overlap with [minecraft-common](../common/README.md): some loaders refuse two JARs
that contain the same package.

## Dependencies

The module depends on `:minecraft_common` and the core modules below it. Loader debug modules compile
its sources and package its resources together with their own, because development runs load the
debug mod without its dependencies; its own JAR is never distributed.

## Translations

Debug messages use their own language files under `src/jvmMain/resources/assets/mahjongcraft/lang`,
kept sorted and checked by the same verification tasks as the main language files.

## Testing

Run this module's tests from the repository root:

```shell
./gradlew :minecraft_common-debug:jvmTest "-PmahjongcraftTarget=minecraft-v1.20.1-fabric"
```
