package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 內建外觀樣式的完整素材描述。 */
object BuiltInMinecraftTileStyles {
    /** 透明牌的外觀樣式 ID。 */
    const val TRANSPARENT_STYLE_ID: String = "${MinecraftModMetadata.MOD_ID}:transparent"

    /** 有透明版貼圖的標準牌面：萬、筒、條一到九與七種字牌。 */
    val TRANSPARENT_TILES: List<Tile> = Tile.Suit.entries.flatMap { suit -> (1..9).map { Tile.Numeric(suit, it) } } + listOf(
        Tile.Honor.East,
        Tile.Honor.South,
        Tile.Honor.West,
        Tile.Honor.North,
        Tile.Honor.White,
        Tile.Honor.Green,
        Tile.Honor.Red,
    )

    /**
     * 透明牌的素材：牌面只留圖案（`mahjong_tile_transparent_<key>`），平面預覽另有墊半透明白底的版本
     * （`mahjong_tile_transparent_<key>_preview`），牌身使用 [BuiltInMinecraftTileAppearances.TRANSPARENT]。
     * 這些貼圖與模型由 Gradle 任務 `generateTransparentTileTextures` 從標準貼圖產生後存檔，標準貼圖改動時需重新產生。
     */
    val TRANSPARENT: List<MinecraftTileStyleAssetDefinition> = TRANSPARENT_TILES.map { tile ->
        val assetKey = transparentAssetKey(tile)
        MinecraftTileStyleAssetDefinition(
            styleId = TRANSPARENT_STYLE_ID,
            tile = tile,
            assetKey = assetKey,
            resources = MinecraftTileResources.builtIn(assetKey).copy(
                previewTextureId = "${MinecraftModMetadata.MOD_ID}:${tileTextureAssetPath("${assetKey}_preview")}",
            ),
            bodyAppearanceId = BuiltInMinecraftTileAppearances.TRANSPARENT.id,
        )
    }

    /**
     * 標準牌面對應的透明版素材 key。
     *
     * @param tile 標準牌面。
     * @return 透明版素材 key，例如 `transparent_m1`。
     */
    fun transparentAssetKey(tile: Tile): String = when (tile) {
        is Tile.Numeric -> "transparent_${tile.standardAssetKey()}"
        is Tile.Honor -> "transparent_${tile.standardAssetKey()}"
        is Tile.Extension -> error("Transparent tile assets only cover standard tiles: $tile")
    }
}
