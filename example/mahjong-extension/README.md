# Common Extension Example

This Kotlin Multiplatform library depends on the public extension API.
It contains no Minecraft, loader or dependency-injection code.
Only the existing JVM target is configured; other runtimes are not verified.

`ExampleMahjongExtension` provides the common extension entrypoint.
The current build skeleton registers no rules or actions.
The Minecraft example packages this library rather than publishing it as another mod.

See the [Minecraft example guide](../../platform/minecraft/example/README.md)
for build and run commands.
