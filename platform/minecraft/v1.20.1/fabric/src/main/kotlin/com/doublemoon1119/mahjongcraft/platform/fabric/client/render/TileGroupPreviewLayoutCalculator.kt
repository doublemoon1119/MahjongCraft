package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

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
