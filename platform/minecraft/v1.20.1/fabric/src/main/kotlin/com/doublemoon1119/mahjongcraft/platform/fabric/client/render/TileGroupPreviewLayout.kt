package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

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
