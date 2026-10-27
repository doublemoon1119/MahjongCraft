# Minecraft Common Example

This library adds Minecraft presentation to the platform-independent extension.
It contains no native Block or Entity implementations and no loader entrypoint.
It registers the pairing rule's settings, names, rule guide, declaration and exchange prompts,
settlement text and tile appearance through MahjongCraft's public extension callbacks.

Translations are provided in Traditional Chinese, Simplified Chinese, English and Japanese.
Shared resources also provide the furniture models, names, creative-tab title and event-chat labels.
The furniture models reuse vanilla oak and blue-wool textures; native behavior belongs to the loader.
The rule contributes only its own settings; MahjongCraft adds shared turn and spectating settings.
The rule-decision time setting is present only while the optional two-tile exchange is enabled.

## Multi-choice Room Setting

The pairing rule uses the shared multi-choice editor for **Tiles to use**.
Choose ranks, apply them to the room draft, then apply the room settings.
The compact list places each name on the left and a small filled checkbox on the right.
Hover a row to read its explanation. Closing without applying keeps the previous draft.
Only selected ranks enter the wall; this is a rule setting, not a visual filter.
The settings summary and history show the selected names in the registered order.

The rule owns the selected values and their network and persistence DTOs.
The presentation maps explicit namespaced option IDs to those values and translation keys;
it does not add a second generic settings payload. See
[`ExamplePairingGameConfigPresentation.kt`](src/commonMain/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/room/ExamplePairingGameConfigPresentation.kt).

The default wall now contains all eight ranks for every supported player count.
This changes the previous smaller two- and three-player default walls.
The example's stored configuration has a required selection field. Older development
saves without it are rejected safely; their format version is unchanged before release.

## Tile Resources

The example owns its models and textures under `assets/mahjongcraft_example/`.
The inverted one-character tile has its own tile ID and face texture; it does not replace the
built-in tile. It inherits the standard model, keeping the same dimensions and table layout.

Registry texture IDs name actual files, for example
`mahjongcraft_example:textures/item/mahjong_tile/mahjong_tile_inverted_one_man.png`.
Model IDs and texture slots inside model JSON use Minecraft's model naming convention instead:
`mahjongcraft_example:item/mahjong_tile/mahjong_tile_inverted_one_man`, without the directory
prefix or file extension. Do not use the model texture name as the registry's texture file ID.

The rule binds a shared cover atlas to both visible and unknown tiles. The unknown face is also
provided by the example; its model remains the shared standard model. Flat back previews use
the back region of the same cover atlas, not another back image.

The default-font addition contains only the example's bitmap provider. Its image stays in the
example namespace; the `minecraft` font path is required to merge that glyph into the default font.

Its classes and resources are packaged into the example mod, not the main mod.
See the [example guide](../README.md) for commands and isolation rules.

The main registration entry point is
[`ExampleMinecraftMahjongExtension.kt`](src/commonMain/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/ExampleMinecraftMahjongExtension.kt).
Resource and presentation tests include
[`ExampleMinecraftRegistrationTest.kt`](src/commonTest/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/ExampleMinecraftRegistrationTest.kt),
[`ExampleMinecraftResourcesTest.kt`](src/jvmTest/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/ExampleMinecraftResourcesTest.kt),
and [`ExampleTileResourcesTest.kt`](src/jvmTest/kotlin/com/doublemoon1119/mahjongcraft/example/minecraft/ExampleTileResourcesTest.kt).

The tile model inherits the standard parent at
[`mahjong_tile_base.json`](../../../../platform/minecraft/common/src/jvmMain/resources/assets/mahjongcraft/models/item/mahjong_tile_base.json).
Furniture model JSON belongs to the example namespace and reuses vanilla oak-plank and blue-wool
textures; the loader module supplies their native behavior.
