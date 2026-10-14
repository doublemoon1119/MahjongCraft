package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 內建牌組外觀的完整資源描述。 */
object BuiltInMinecraftTileAppearances {
    /** 內建標準牌組外觀。 */
    val DEFAULT: MinecraftTileSetAppearanceDefinition = MinecraftTileSetAppearanceDefinition(
        id = BuiltInMinecraftTileAppearanceIds.DEFAULT,
        coverTextureId = "${MinecraftModMetadata.MOD_ID}:${tileTextureAssetPath("cover")}",
    )
}
