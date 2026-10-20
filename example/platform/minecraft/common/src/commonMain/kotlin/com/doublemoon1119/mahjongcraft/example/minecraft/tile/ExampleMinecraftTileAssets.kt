package com.doublemoon1119.mahjongcraft.example.minecraft.tile

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftRuleTileAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileResources
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelText

/**
 * 登記範例牌組的共用外觀與簡易配對麻將使用的倒置一萬牌面。
 *
 * @param registry 保存牌面資產與牌組外觀的 registry。
 */
fun registerExampleTileAssets(registry: MinecraftTileAssetRegistry) {
    registry.register(
        MinecraftTileSetAppearanceDefinition(
            id = EXAMPLE_TILE_SET_APPEARANCE_ID,
            coverTextureId = "$EXAMPLE_ASSET_NAMESPACE:textures/item/mahjong_tile/mahjong_tile_cover.png",
            unknownFaceTextureId = "$EXAMPLE_ASSET_NAMESPACE:textures/item/mahjong_tile/mahjong_tile_unknown.png",
        ),
    )
    registry.register(
        MinecraftRuleTileAppearanceDefinition(
            ruleModuleId = ExamplePairingIds.RULE_MODULE,
            appearanceId = EXAMPLE_TILE_SET_APPEARANCE_ID,
        ),
    )
    registry.register(
        MinecraftTileAssetDefinition(
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
fun registerExampleTileLabels(registry: TileLabelRegistry) {
    registry.register(
        TileLabelDefinition(
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
fun registerExampleTileEmojis(registry: TileEmojiRegistry) {
    registry.register(TileEmojiDefinition(INVERTED_ONE_MAN_ASSET_KEY, "\uDB82\uDE00"))
}

/** 範例牌面資源所使用的 namespace。 */
private const val EXAMPLE_ASSET_NAMESPACE = ExampleExtensionIds.MOD_ID

/** 範例牌組共用外觀的完整識別碼。 */
private const val EXAMPLE_TILE_SET_APPEARANCE_ID = "$EXAMPLE_ASSET_NAMESPACE:pairing"

/** 倒置一萬在牌資產 registry 中使用的素材 key。 */
private const val INVERTED_ONE_MAN_ASSET_KEY = "$EXAMPLE_ASSET_NAMESPACE:inverted_one_man"
