# Fabric 1.20.1 Example

This is the independently packaged example mod, with ID `mahjongcraft_example`.
It depends on MahjongCraft and combines the two example libraries into its own JAR.
It does not bundle MahjongCraft, its dependencies or another copy of Kotlin.

The entrypoints load the platform-independent pairing rule and its shared Minecraft presentation.
The main initializer registers the chair, cushion, creative tab and public game-event listeners.
The client initializer registers furniture renderers only; the dedicated server does not load them.
Seat providers are registered through the extension callback without reading native content during
construction. World lookup and seating occur only when the provider is used.

The block chair creates a temporary invisible support only when seating succeeds. The cushion is
the seat entity itself and retains its UUID when the world is saved and loaded. Both use the public
seat contracts rather than MahjongCraft's internal stool implementation or dependency container.
See the [example guide](../../README.md) for commands and development-world isolation.
