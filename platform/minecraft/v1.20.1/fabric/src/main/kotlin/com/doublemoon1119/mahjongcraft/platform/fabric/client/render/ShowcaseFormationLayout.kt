package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * 役滿 showcase 的最終編隊：各翼並排、整體以 x = 0 置中，和牌張依翼數放在固定位置。
 *
 * - 一翼：和牌張在該翼手牌的 x 較小一側。
 * - 兩翼：兩翼左右分開，和牌張在正中央的間隙中。
 * - 三翼：和牌張在中央那一翼手牌的 x 較小一側。
 *
 * 同一翼中卡片依組別分段，組與組之間多留 [ShowcaseFormationMetrics.groupGap]。承載和牌張的那一翼會把和牌張
 * 算進自身寬度，因此相鄰翼不會與和牌張重疊。沒有卡片的翼仍保留一張卡片的寬度。只處理數字，不碰 entity 或繪製。
 *
 * @property wingCardOrders 各翼卡片的 [ShowcaseCardSlot.Hand.order]，外層順序即翼的索引。
 * @param includesWinningTile 歸位順序是否包含和牌張；和牌張的位置 [winningTileX] 不受影響。
 * @property metrics 尺寸參數。
 * @property wingCenters 各翼中心的 X 座標，依翼索引排列。
 * @property winningTileX 和牌張在編隊中的 X 座標。
 * @property returnOrder 爆炸後各牌張依序歸位的順序。
 * @param wingCardGroups 與 [wingCardOrders] 一一對應的組別；組別依 order 遞增且不遞減，省略時全部同一組。
 */
class ShowcaseFormationLayout(
    private val wingCardOrders: List<List<Int>>,
    includesWinningTile: Boolean,
    private val metrics: ShowcaseFormationMetrics,
    wingCardGroups: List<List<Int>> = wingCardOrders.map { orders -> orders.map { 0 } },
) {
    /** 各翼中 order 對應的組別。 */
    private val groupByOrder: List<Map<Int, Int>> = wingCardOrders.mapIndexed { wingIndex, orders ->
        orders.zip(wingCardGroups.getOrElse(wingIndex) { emptyList() }).toMap()
    }

    /** 各翼中心的 X 座標，依翼索引排列。 */
    val wingCenters: List<Double> = computeWingCenters()

    /** 和牌張在編隊中的 X 座標。 */
    val winningTileX: Double = when (wingCardOrders.size) {
        1 -> wingCenters[0] + winningTileRelativeX(0)
        3 -> wingCenters[1] + winningTileRelativeX(1)
        else -> 0.0
    }

    /**
     * 爆炸後飛回編隊的順序：x 較大的先動；x 相同時和牌張排在後面。
     *
     * 每張卡片各佔一個順位，同一翼中重複的 order 也不合併。
     *
     * @property returnOrder 每張手牌及（若納入）和牌張的歸位次序。
     */
    val returnOrder: List<ShowcaseCardSlot> = buildList {
        wingCardOrders.forEachIndexed { wingIndex, orders ->
            orders.forEach { add(ShowcaseCardSlot.Hand(wingIndex, it)) }
        }
        if (includesWinningTile) add(ShowcaseCardSlot.WinningTile)
    }.sortedWith(compareByDescending<ShowcaseCardSlot> { targetX(it) }.thenBy { if (it is ShowcaseCardSlot.WinningTile) 1 else 0 })

    /**
     * 牌位在編隊中的 x。
     *
     * @param slot 要查詢的牌位。
     * @return 該牌位在編隊中的 X 座標。
     */
    fun targetX(slot: ShowcaseCardSlot): Double = when (slot) {
        is ShowcaseCardSlot.Hand -> wingCenters[slot.wingIndex] + centerSpan(slot.wingIndex) / 2.0 - offsetInWing(slot.wingIndex, slot.order)
        ShowcaseCardSlot.WinningTile -> winningTileX
    }

    /**
     * 指定翼相對於自身中心的水平範圍，含它承載的和牌張。
     *
     * @param wingIndex 翼索引。
     * @return 該翼左右邊界相對中心的範圍。
     */
    fun wingBounds(wingIndex: Int): ShowcaseHorizontalBounds {
        val handSpan = centerSpan(wingIndex) + metrics.cardWidth
        val halfWidth = maxOf(handSpan, metrics.minimumWingWidth) / 2.0
        val minX = if (hostsWinningTile(wingIndex)) {
            minOf(-halfWidth, winningTileRelativeX(wingIndex) - metrics.winningTileWidth / 2.0)
        } else {
            -halfWidth
        }
        return ShowcaseHorizontalBounds(minX, halfWidth)
    }

    /**
     * 兩翼時各自往外讓出中央間隙；其他翼數則依序並排後整體置中。
     *
     * @return 各翼中心的水平座標。
     */
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

    /**
     * 和牌張相對於承載翼中心的 x：緊鄰 x 最小的卡片，中間隔 [ShowcaseFormationMetrics.winningTileGap]。
     *
     * @param wingIndex 承載和牌張的翼索引。
     * @return 和牌張相對該翼中心的 X 座標。
     */
    private fun winningTileRelativeX(wingIndex: Int): Double = -centerSpan(wingIndex) / 2.0 -
        metrics.cardWidth / 2.0 - metrics.winningTileGap - metrics.winningTileWidth / 2.0

    /**
     * 卡片沿排列方向的位移：order 乘上卡片間距，再加上前面各組的組間空隙。
     *
     * @param wingIndex 卡片所屬翼索引。
     * @param order 卡片在翼中的順序。
     * @return 從翼中第一張卡片起算的水平位移。
     */
    private fun offsetInWing(wingIndex: Int, order: Int): Double = order * metrics.cardSpacing + ((groupByOrder[wingIndex][order] ?: 0) - firstGroup(wingIndex)) * metrics.groupGap

    /**
     * 一翼第一張到最後一張卡片中心的距離；沒有卡片的翼為 0。
     *
     * @param wingIndex 要查詢的翼索引。
     * @return 該翼卡片中心跨度。
     */
    private fun centerSpan(wingIndex: Int): Double {
        val orders = wingCardOrders[wingIndex]
        if (orders.isEmpty()) return 0.0
        val groups = groupByOrder[wingIndex].values
        val groupSpan = (groups.maxOrNull() ?: 0) - (groups.minOrNull() ?: 0)
        return (orders.size - 1) * metrics.cardSpacing + groupSpan * metrics.groupGap
    }

    /**
     * 一翼中最小的組別。
     *
     * @param wingIndex 要查詢的翼索引。
     * @return 該翼最小組別編號；沒有組別時為 0。
     */
    private fun firstGroup(wingIndex: Int): Int = groupByOrder[wingIndex].values.minOrNull() ?: 0

    /**
     * 一翼是否承載和牌張；只由翼數決定。
     *
     * @param wingIndex 要查詢的翼索引。
     * @return 此翼依目前翼數配置承載和牌張時為 `true`。
     */
    private fun hostsWinningTile(wingIndex: Int): Boolean = (wingCardOrders.size == 1 && wingIndex == 0) || (wingCardOrders.size == 3 && wingIndex == 1)
}
