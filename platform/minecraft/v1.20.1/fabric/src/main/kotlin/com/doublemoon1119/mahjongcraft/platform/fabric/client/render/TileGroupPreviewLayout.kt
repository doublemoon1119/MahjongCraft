package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto

/** 一張牌在橫向牌組預覽中的宣告式輸入。 */
data class TileGroupPreviewEntry(
    val assetKey: String,
    val orientation: DecisionTileOrientationDto = DecisionTileOrientationDto.UPRIGHT,
    val stacked: Boolean = false,
)

/** 一張牌經過牌組排列後的中心位置與方向。 */
data class TileGroupPreviewPlacement(
    val assetKey: String,
    val centerX: Float,
    val centerY: Float,
    val orientation: DecisionTileOrientationDto,
)

/** 橫向牌組預覽的完整測量與排列結果。 */
data class TileGroupPreviewLayout(
    val contentWidth: Float,
    val contentHeight: Float,
    val placements: List<TileGroupPreviewPlacement>,
)

/** 統一計算操作 HUD 與副露提示使用的橫向牌組排列。 */
object TileGroupPreviewLayoutCalculator {
    /**
     * 依牌面尺寸與間距建立置中的牌組排列；疊放牌（加槓補上的第 4 張）依各自方向換算實際螢幕高度，
     * 緊鄰錨點牌堆疊——橫置牌旋轉 90 度後螢幕高度等於原本的牌寬，用固定的直立牌高度間距會讓橫置的
     * 錨點牌與疊放牌互相重疊。
     */
    fun calculate(
        entries: List<TileGroupPreviewEntry>,
        tileWidth: Float,
        tileHeight: Float,
        gap: Float,
    ): TileGroupPreviewLayout {
        val baseEntries = entries.filterNot(TileGroupPreviewEntry::stacked)
        if (baseEntries.isEmpty()) return TileGroupPreviewLayout(0f, 0f, emptyList())
        val widths = baseEntries.map { entry ->
            if (entry.orientation == DecisionTileOrientationDto.UPRIGHT) tileWidth else tileHeight
        }
        val contentWidth = widths.sum() + gap * (widths.size - 1).coerceAtLeast(0)
        val basePlacements = mutableListOf<TileGroupPreviewPlacement>()
        var cursor = -contentWidth / 2f
        baseEntries.forEachIndexed { index, entry ->
            val occupiedWidth = widths[index]
            basePlacements += TileGroupPreviewPlacement(
                assetKey = entry.assetKey,
                centerX = cursor + occupiedWidth / 2f,
                centerY = if (entry.orientation == DecisionTileOrientationDto.UPRIGHT) 0f else (tileHeight - tileWidth) / 2f,
                orientation = entry.orientation,
            )
            cursor += occupiedWidth + gap
        }
        val stackAnchor = basePlacements.firstOrNull { it.orientation != DecisionTileOrientationDto.UPRIGHT }
            ?: basePlacements.last()
        var stackCursorY = stackAnchor.centerY - onScreenHalfHeight(stackAnchor.orientation, tileWidth, tileHeight)
        val stackedPlacements = entries.filter(TileGroupPreviewEntry::stacked).map { entry ->
            val half = onScreenHalfHeight(entry.orientation, tileWidth, tileHeight)
            stackCursorY -= gap + half
            val placement = TileGroupPreviewPlacement(entry.assetKey, stackAnchor.centerX, stackCursorY, entry.orientation)
            stackCursorY -= half
            placement
        }
        val placements = basePlacements + stackedPlacements
        val minimumY = placements.minOf { placement -> placement.centerY - onScreenHalfHeight(placement.orientation, tileWidth, tileHeight) }
        val maximumY = placements.maxOf { placement -> placement.centerY + onScreenHalfHeight(placement.orientation, tileWidth, tileHeight) }
        // 加槓疊放牌只往其中一側延伸，實際內容的垂直範圍不會自然對稱分佈在 0 兩側；呼叫端把背景畫在
        // 「置中於 0」的假設下，這裡把每張牌的 centerY 平移，讓內容垂直中心真正落在 0，背景才會跟牌面
        // 對齊。
        val verticalCenter = (minimumY + maximumY) / 2f
        val centeredPlacements = placements.map { placement -> placement.copy(centerY = placement.centerY - verticalCenter) }
        return TileGroupPreviewLayout(contentWidth, maximumY - minimumY, centeredPlacements)
    }

    /** 一張牌在目前 2D 排列中的實際螢幕高度一半；橫置牌旋轉 90 度後，螢幕高度等於原本的牌寬。 */
    private fun onScreenHalfHeight(orientation: DecisionTileOrientationDto, tileWidth: Float, tileHeight: Float): Float = if (orientation == DecisionTileOrientationDto.UPRIGHT) tileHeight / 2f else tileWidth / 2f
}
