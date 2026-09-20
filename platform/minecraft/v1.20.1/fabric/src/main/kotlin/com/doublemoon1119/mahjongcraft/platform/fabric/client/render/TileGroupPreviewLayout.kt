package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * 一張牌在橫向牌組預覽中的宣告式輸入。
 *
 * @property assetKey 牌面資產。
 * @property claimed 是否為鳴取自他家的那張牌，決定要不要畫鳴牌指標。
 */
data class TileGroupPreviewEntry(
    val assetKey: String,
    val claimed: Boolean = false,
)

/**
 * 一張牌經過牌組排列後的中心位置。
 *
 * @property assetKey 牌面資產。
 * @property centerX 水平中心。
 * @property centerY 垂直中心。
 * @property claimed 是否要在這張牌上方畫鳴牌指標。
 */
data class TileGroupPreviewPlacement(
    val assetKey: String,
    val centerX: Float,
    val centerY: Float,
    val claimed: Boolean,
)

/**
 * 橫向牌組預覽的完整測量與排列結果。
 *
 * @property contentWidth 內容總寬度。
 * @property contentHeight 內容總高度。
 * @property placements 每張牌的位置。
 */
data class TileGroupPreviewLayout(
    val contentWidth: Float,
    val contentHeight: Float,
    val placements: List<TileGroupPreviewPlacement>,
)

/** 計算鳴牌提示使用的橫向牌組排列：一列等距的直立牌，水平與垂直皆置中於原點。 */
object TileGroupPreviewLayoutCalculator {
    /**
     * 依牌面尺寸與間距建立置中的牌組排列。
     *
     * 牌一律直立、依傳入順序排列，比照決策卡片的呈現；鳴取的那張由 [TileGroupPreviewEntry.claimed] 指出，
     * 位置不因此改變。
     */
    fun calculate(
        entries: List<TileGroupPreviewEntry>,
        tileWidth: Float,
        tileHeight: Float,
        gap: Float,
    ): TileGroupPreviewLayout {
        if (entries.isEmpty()) return TileGroupPreviewLayout(0f, 0f, emptyList())
        val contentWidth = tileWidth * entries.size + gap * (entries.size - 1)
        var cursor = -contentWidth / 2f
        val placements = entries.map { entry ->
            val placement = TileGroupPreviewPlacement(
                assetKey = entry.assetKey,
                centerX = cursor + tileWidth / 2f,
                centerY = 0f,
                claimed = entry.claimed,
            )
            cursor += tileWidth + gap
            placement
        }
        return TileGroupPreviewLayout(contentWidth, tileHeight, placements)
    }
}
