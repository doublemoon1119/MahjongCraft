# Fabric 1.20.1 Example

This is the independently packaged example mod, with ID `mahjongcraft_example`.
It depends on MahjongCraft and combines the two example libraries into its own JAR.
It does not bundle MahjongCraft, its dependencies or another copy of Kotlin.

The entrypoints load the platform-independent pairing rule and its shared Minecraft presentation.
Native example furniture, its renderers and event chat output are not registered yet.
See the [example guide](../../README.md) for commands and development-world isolation.
