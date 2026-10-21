# Extension Examples

This directory contains MahjongCraft extension examples and their platform adapters.
It is not part of the default build or the published MahjongCraft mod.

- [Mahjong extension](mahjong-extension/README.md): common extension implementation.
- [Minecraft integration](platform/minecraft/README.md): platform presentation,
  loader entrypoints and development runs.

The common library includes a small pairing rule, custom tile, declaration,
optional tile exchange and Tsumogiri strategy.
The Minecraft adapter supplies settings, text and tile resources.
The Minecraft adapter also provides a block chair, a placeable cushion and administrator-only
event chat output. Its creative tab groups both furniture items together.
