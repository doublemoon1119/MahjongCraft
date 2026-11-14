package com.doublemoon1119.mahjongcraft.example.minecraft.tile

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingComponentIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.MinecraftTileAssetRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileEmojiRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileLabelRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftRuleTileAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileResources
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileStyleAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelText
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.standardAssetKey

/**
 * 登記範例牌組的共用外觀、簡易配對麻將使用的倒置一萬牌面，以及公開牌面使用的不同顏色牌身。
 *
 * 公開牌面樣式沿用一萬至八萬的基本牌面資源，只把牌身換成 [PUBLIC_FACE_BODY_APPEARANCE_ID]。
 *
 * @param registry 保存牌面資產與牌組外觀的 registry。
 */
fun registerExampleTileAssets(registry: MinecraftTileAssetRegistrar) {
    registry.register(
        definition = MinecraftTileSetAppearanceDefinition(
            id = EXAMPLE_TILE_SET_APPEARANCE_ID,
            coverTextureId = "$EXAMPLE_ASSET_NAMESPACE:textures/item/mahjong_tile/mahjong_tile_cover.png",
            unknownFaceTextureId = "$EXAMPLE_ASSET_NAMESPACE:textures/item/mahjong_tile/mahjong_tile_unknown.png",
        ),
    )
    registry.register(
        definition = MinecraftRuleTileAppearanceDefinition(
            ruleModuleId = ExamplePairingIds.RULE_MODULE,
            appearanceId = EXAMPLE_TILE_SET_APPEARANCE_ID,
        ),
    )
    registry.register(
        definition = MinecraftTileSetAppearanceDefinition(
            id = PUBLIC_FACE_BODY_APPEARANCE_ID,
            coverTextureId = "$EXAMPLE_ASSET_NAMESPACE:textures/item/mahjong_tile/mahjong_tile_cover_public_face.png",
            unknownFaceTextureId = "$EXAMPLE_ASSET_NAMESPACE:textures/item/mahjong_tile/mahjong_tile_unknown.png",
        ),
    )
    (1..8).forEach { value ->
        val tile = Tile.Numeric(Tile.Suit.Character, value)
        registry.register(
            definition = MinecraftTileStyleAssetDefinition(
                styleId = ExamplePairingComponentIds.PUBLIC_FACE_APPEARANCE,
                tile = tile,
                assetKey = "$EXAMPLE_ASSET_NAMESPACE:public_face_m$value",
                resources = MinecraftTileResources.builtIn(tile.standardAssetKey()),
                bodyAppearanceId = PUBLIC_FACE_BODY_APPEARANCE_ID,
            ),
        )
    }
    registry.register(
        definition = MinecraftTileAssetDefinition(
            typeId = ExamplePairingIds.INVERTED_ONE_MAN,
            assetKey = INVERTED_ONE_MAN_ASSET_KEY,
            resources = MinecraftTileResources(
                textureId = "$EXAMPLE_ASSET_NAMESPACE:textures/item/mahjong_tile/mahjong_tile_inverted_one_man.png",
                modelId = "$EXAMPLE_ASSET_NAMESPACE:item/mahjong_tile/mahjong_tile_inverted_one_man",
            ),
        ),
    )
}

/**
 * 登記範例倒置一萬的牌角數字標籤。
 *
 * @param registry 保存牌面角落標籤的 registry。
 */
fun registerExampleTileLabels(registry: TileLabelRegistrar) {
    registry.register(
        definition = TileLabelDefinition(
            assetKey = INVERTED_ONE_MAN_ASSET_KEY,
            label = TileLabel(topLeft = TileLabelText("1", TileLabelColor.RED), topRight = null),
        ),
    )
}

/**
 * 登記範例倒置一萬使用的自訂 Mahjong Tiles 字元。
 *
 * @param registry 保存牌面 emoji 對照的 registry。
 */
fun registerExampleTileEmojis(registry: TileEmojiRegistrar) {
    registry.register(TileEmojiDefinition(INVERTED_ONE_MAN_ASSET_KEY, "\uDB82\uDE00"))
}

/** 範例牌面資源所使用的 namespace。 */
private const val EXAMPLE_ASSET_NAMESPACE = ExampleExtensionIds.MOD_ID

/** 範例牌組共用外觀的完整識別碼。 */
private const val EXAMPLE_TILE_SET_APPEARANCE_ID = "$EXAMPLE_ASSET_NAMESPACE:pairing"

/** 公開牌面樣式的牌身外觀識別碼。 */
private const val PUBLIC_FACE_BODY_APPEARANCE_ID = "$EXAMPLE_ASSET_NAMESPACE:pairing/public_face_body"

/** 倒置一萬在牌資產 registry 中使用的素材 key。 */
private const val INVERTED_ONE_MAN_ASSET_KEY = "$EXAMPLE_ASSET_NAMESPACE:inverted_one_man"
