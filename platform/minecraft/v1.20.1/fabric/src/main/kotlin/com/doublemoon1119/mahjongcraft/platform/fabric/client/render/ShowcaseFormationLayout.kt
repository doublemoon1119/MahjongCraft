package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/** showcase 編隊中的一個牌位。 */
sealed interface ShowcaseCardSlot {
    /**
     * 某一翼的手牌卡片。
     *
     * @property wingIndex 所屬翼的索引。
     * @property order 卡片在翼中的順序；0 的 x 最大，依序往 x 較小的一側排。
     */
    data class Hand(
        val wingIndex: Int,
        val order: Int,
    ) : ShowcaseCardSlot

    /** 全部翼共用的一張和牌張。 */
    data object WinningTile : ShowcaseCardSlot
}

/**
 * showcase 編隊的尺寸參數，單位為方塊。
 *
 * @property cardSpacing 同一翼相鄰卡片的中心距。
 * @property cardWidth 展示卡片的寬度。
 * @property winningTileWidth 和牌張的寬度。
 * @property winningTileGap 和牌張與手牌之間的間隙。
 * @property minimumWingWidth 一翼的最小寬度，讓役名標題放得下。
 * @property wingGap 相鄰兩翼之間的間隙。
 */
data class ShowcaseFormationMetrics(
    val cardSpacing: Double,
    val cardWidth: Double,
    val winningTileWidth: Double,
    val winningTileGap: Double,
    val minimumWingWidth: Double,
    val wingGap: Double,
)

/**
 * 一翼相對於自身中心的水平範圍。
 *
 * @property minX 左緣。
 * @property maxX 右緣。
 */
data class ShowcaseHorizontalBounds(
    val minX: Double,
    val maxX: Double,
) {
    /** 寬度。 */
    val width: Double get() = maxX - minX
}

/**
 * 役滿 showcase 的最終編隊：各翼並排、整體以 x = 0 置中，和牌張依翼數放在固定位置。
 *
 * - 一翼：和牌張在該翼手牌的 x 較小一側。
 * - 兩翼：兩翼左右分開，和牌張在正中央的間隙中。
 * - 三翼：和牌張在中央那一翼手牌的 x 較小一側。
 *
 * 承載和牌張的那一翼會把和牌張算進自身寬度，因此相鄰翼不會與和牌張重疊。沒有卡片的翼仍保留一張卡片的
 * 寬度。只處理數字，不碰 entity 或繪製。
 *
 * @param wingCardOrders 各翼卡片的 [ShowcaseCardSlot.Hand.order]，外層順序即翼的索引。
 * @param includesWinningTile 歸位順序是否包含和牌張；和牌張的位置 [winningTileX] 不受影響。
 * @param metrics 尺寸參數。
 */
class ShowcaseFormationLayout(
    private val wingCardOrders: List<List<Int>>,
    includesWinningTile: Boolean,
    private val metrics: ShowcaseFormationMetrics,
) {
    /** 各翼中心的 x。 */
    val wingCenters: List<Double> = computeWingCenters()

    /** 和牌張的 x。 */
    val winningTileX: Double = when (wingCardOrders.size) {
        1 -> wingCenters[0] + winningTileRelativeX(cardCount(0))
        3 -> wingCenters[1] + winningTileRelativeX(cardCount(1))
        else -> 0.0
    }

    /**
     * 爆炸後飛回編隊的順序：x 較大的先動；x 相同時和牌張排在後面。
     *
     * 每張卡片各佔一個順位，同一翼中重複的 order 也不合併。
     */
    val returnOrder: List<ShowcaseCardSlot> = buildList {
        wingCardOrders.forEachIndexed { wingIndex, orders ->
            orders.forEach { add(ShowcaseCardSlot.Hand(wingIndex, it)) }
        }
        if (includesWinningTile) add(ShowcaseCardSlot.WinningTile)
    }.sortedWith(compareByDescending<ShowcaseCardSlot> { targetX(it) }.thenBy { if (it is ShowcaseCardSlot.WinningTile) 1 else 0 })

    /** 牌位在編隊中的 x。 */
    fun targetX(slot: ShowcaseCardSlot): Double = when (slot) {
        is ShowcaseCardSlot.Hand ->
            wingCenters[slot.wingIndex] + ((wingCardOrders[slot.wingIndex].size - 1) / 2.0 - slot.order) * metrics.cardSpacing
        ShowcaseCardSlot.WinningTile -> winningTileX
    }

    /** 指定翼相對於自身中心的水平範圍，含它承載的和牌張。 */
    fun wingBounds(wingIndex: Int): ShowcaseHorizontalBounds {
        val cardCount = cardCount(wingIndex)
        val handSpan = (cardCount - 1) * metrics.cardSpacing + metrics.cardWidth
        val halfWidth = maxOf(handSpan, metrics.minimumWingWidth) / 2.0
        val minX = if (hostsWinningTile(wingIndex)) {
            minOf(-halfWidth, winningTileRelativeX(cardCount) - metrics.winningTileWidth / 2.0)
        } else {
            -halfWidth
        }
        return ShowcaseHorizontalBounds(minX, halfWidth)
    }

    /** 兩翼時各自往外讓出中央間隙；其他翼數則依序並排後整體置中。 */
    private fun computeWingCenters(): List<Double> {
        val wingCount = wingCardOrders.size
        if (wingCount == 2) {
            val halfGap = maxOf(metrics.wingGap, metrics.winningTileWidth + metrics.winningTileGap * 2.0) / 2.0
            val leftHalf = wingBounds(0).width / 2.0
            val rightHalf = wingBounds(1).width / 2.0
            return listOf(-(halfGap + leftHalf), halfGap + rightHalf)
        }
        var cursor = 0.0
        val centers = wingCardOrders.indices.map { wingIndex ->
            val bounds = wingBounds(wingIndex)
            (cursor - bounds.minX).also { cursor += bounds.width + metrics.wingGap }
        }
        val totalWidth = cursor - metrics.wingGap
        return centers.map { it - totalWidth / 2.0 }
    }

    /** 和牌張相對於承載翼中心的 x：緊鄰 x 最小的卡片，中間隔 [ShowcaseFormationMetrics.winningTileGap]。 */
    private fun winningTileRelativeX(handCardCount: Int): Double = -(handCardCount - 1) / 2.0 * metrics.cardSpacing -
        metrics.cardWidth / 2.0 - metrics.winningTileGap - metrics.winningTileWidth / 2.0

    /** 一翼是否承載和牌張；只由翼數決定。 */
    private fun hostsWinningTile(wingIndex: Int): Boolean = (wingCardOrders.size == 1 && wingIndex == 0) || (wingCardOrders.size == 3 && wingIndex == 1)

    /** 排版用的卡片數；沒有卡片的翼以一張計算。 */
    private fun cardCount(wingIndex: Int): Int = wingCardOrders[wingIndex].size.coerceAtLeast(1)
}
