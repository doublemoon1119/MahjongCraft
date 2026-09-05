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
    /** 依牌面尺寸與間距建立置中的牌組排列。 */
    fun calculate(
        entries: List<TileGroupPreviewEntry>,
        tileWidth: Float,
        tileHeight: Float,
        gap: Float,
        stackedOffsetY: Float = -tileWidth * 0.35f,
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
        val stackedPlacements = entries.filter(TileGroupPreviewEntry::stacked).mapIndexed { index, entry ->
            TileGroupPreviewPlacement(
                assetKey = entry.assetKey,
                centerX = stackAnchor.centerX,
                centerY = stackAnchor.centerY + stackedOffsetY * (index + 1),
                orientation = entry.orientation,
            )
        }
        val placements = basePlacements + stackedPlacements
        val minimumY = placements.minOf { placement ->
            placement.centerY - if (placement.orientation == DecisionTileOrientationDto.UPRIGHT) tileHeight / 2f else tileWidth / 2f
        }
        val maximumY = placements.maxOf { placement ->
            placement.centerY + if (placement.orientation == DecisionTileOrientationDto.UPRIGHT) tileHeight / 2f else tileWidth / 2f
        }
        return TileGroupPreviewLayout(contentWidth, maximumY - minimumY, placements)
    }
}
