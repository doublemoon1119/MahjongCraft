package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * 排名列中的一欄。
 *
 * @property id 欄位識別。
 * @property width 欄位寬度。
 * @property gapBefore 與前一欄之間的間距；第一欄的值不使用。
 */
data class SettlementRankingColumn(
    val id: SettlementRankingColumnId,
    val width: Float,
    val gapBefore: Float,
)
