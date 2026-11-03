package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

/**
 * 模型預覽圖片中要當作頭像使用的正規化區域。
 *
 * @property left 左側 UV。
 * @property top 上側 UV。
 * @property right 右側 UV。
 * @property bottom 下側 UV。
 */
class FabricAiCharacterPreviewRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f } && left < right && top < bottom) { "Invalid preview portrait region" }
    }
}
