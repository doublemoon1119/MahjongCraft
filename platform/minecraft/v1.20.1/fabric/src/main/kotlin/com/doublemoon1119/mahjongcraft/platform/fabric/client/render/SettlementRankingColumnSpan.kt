package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * 一欄在面板座標中的水平範圍；面板以 x = 0 為中心。
 *
 * @property left 左緣。
 * @property width 寬度。
 */
data class SettlementRankingColumnSpan(
    val left: Float,
    val width: Float,
) {
    /** 右緣。 */
    val right: Float get() = left + width

    /** 水平中心。 */
    val centerX: Float get() = left + width / 2f
}
