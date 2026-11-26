package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 內建牌組外觀的完整資源描述。 */
object BuiltInMinecraftTileAppearances {
    /** 內建標準牌組外觀。 */
    val DEFAULT: MinecraftTileSetAppearanceDefinition = MinecraftTileSetAppearanceDefinition(
        id = BuiltInMinecraftTileAppearanceIds.DEFAULT,
        coverTextureId = "${MinecraftModMetadata.MOD_ID}:${tileTextureAssetPath("cover")}",
    )

    /** 透明牌的牌身：幾乎全透明的白色牌身，由 Gradle 任務 `generateTransparentTileTextures` 從標準牌身產生。 */
    val TRANSPARENT: MinecraftTileSetAppearanceDefinition = MinecraftTileSetAppearanceDefinition(
        id = "${MinecraftModMetadata.MOD_ID}:transparent_body",
        coverTextureId = "${MinecraftModMetadata.MOD_ID}:${tileTextureAssetPath("cover_transparent")}",
        translucent = true,
    )
}
