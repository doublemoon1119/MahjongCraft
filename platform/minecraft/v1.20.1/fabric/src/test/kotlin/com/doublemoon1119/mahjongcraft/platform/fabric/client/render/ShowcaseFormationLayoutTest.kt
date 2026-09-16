package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ShowcaseCardSlot.Hand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ShowcaseCardSlot.WinningTile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證役滿 showcase 的編隊幾何。 */
class ShowcaseFormationLayoutTest {
    /** 一翼時和牌張併入該翼寬度，整個編隊（含和牌張）左右對稱。 */
    @Test
    fun `centres a single wing together with its winning tile`() {
        val layout = layout(listOf(0, 1, 2))

        assertEquals(ShowcaseHorizontalBounds(minX = -3.0, maxX = 1.5), layout.wingBounds(0))
        assertEquals(listOf(0.75), layout.wingCenters)
        assertEquals(-1.75, layout.winningTileX)
        assertEquals(-2.25, layout.wingCenters[0] + layout.wingBounds(0).minX)
        assertEquals(2.25, layout.wingCenters[0] + layout.wingBounds(0).maxX)
    }

    /** 卡片依 order 往 x 較小的一側排，間距固定；和牌張緊鄰最後一張。 */
    @Test
    fun `lays the cards of a wing out by order`() {
        val layout = layout(listOf(0, 1, 2))

        assertEquals(listOf(1.75, 0.75, -0.25), (0..2).map { layout.targetX(Hand(0, it)) })
        assertEquals(layout.targetX(Hand(0, 2)) - 1.5, layout.targetX(WinningTile))
    }

    /** 兩翼時左右分開，和牌張在正中央。 */
    @Test
    fun `splits two wings around a centred winning tile`() {
        val layout = layout(listOf(0, 1), listOf(0))

        assertEquals(listOf(-2.0, 2.0), layout.wingCenters)
        assertEquals(0.0, layout.winningTileX)
        assertEquals(listOf(-1.5, -2.5), (0..1).map { layout.targetX(Hand(0, it)) })
        assertEquals(2.0, layout.targetX(Hand(1, 0)))
    }

    /** 兩翼的中央間隙至少放得下和牌張與兩側間隙。 */
    @Test
    fun `keeps room for the winning tile between two wings`() {
        val layout = ShowcaseFormationLayout(
            wingCardOrders = listOf(listOf(0), listOf(0)),
            includesWinningTile = true,
            metrics = METRICS.copy(wingGap = 0.5),
        )

        val leftEdge = layout.wingCenters[0] + layout.wingBounds(0).maxX
        val rightEdge = layout.wingCenters[1] + layout.wingBounds(1).minX
        assertEquals(2.0, rightEdge - leftEdge)
    }

    /** 三翼時和牌張屬於中央那一翼，相鄰翼之間剛好隔一個翼間距。 */
    @Test
    fun `puts the winning tile inside the middle of three wings`() {
        val layout = layout(listOf(0), listOf(0), listOf(0))

        assertEquals(listOf(-4.5, 0.5, 4.5), layout.wingCenters)
        assertEquals(-1.0, layout.winningTileX)
        assertEquals(ShowcaseHorizontalBounds(minX = -2.0, maxX = 1.0), layout.wingBounds(1))
        val edges = layout.wingCenters.indices.map { layout.wingCenters[it] + layout.wingBounds(it).minX to layout.wingCenters[it] + layout.wingBounds(it).maxX }
        assertEquals(listOf(2.0, 2.0), edges.zipWithNext().map { (left, right) -> right.first - left.second })
        assertEquals(-edges.first().first, edges.last().second)
    }

    /** 沒有卡片的翼仍保留一張卡片與最小翼寬。 */
    @Test
    fun `keeps a card width for an empty wing`() {
        val layout = layout(emptyList())

        assertEquals(ShowcaseHorizontalBounds(minX = -2.0, maxX = 1.0), layout.wingBounds(0))
        assertEquals(-1.0, layout.winningTileX)
    }

    /** 手牌少時翼寬由最小翼寬決定，讓標題放得下。 */
    @Test
    fun `widens a short wing to the minimum wing width`() {
        val layout = ShowcaseFormationLayout(
            wingCardOrders = listOf(listOf(0), listOf(0)),
            includesWinningTile = false,
            metrics = METRICS.copy(minimumWingWidth = 6.0),
        )

        assertEquals(6.0, layout.wingBounds(0).width)
    }

    /** 歸位順序依 x 由大到小。 */
    @Test
    fun `returns the cards from the largest x`() {
        val layout = layout(listOf(0, 1), listOf(0))

        assertEquals(listOf(Hand(1, 0), WinningTile, Hand(0, 0), Hand(0, 1)), layout.returnOrder)
    }

    /** x 相同時和牌張排在後面。 */
    @Test
    fun `returns the winning tile after a card at the same x`() {
        val layout = layout(listOf(0, 1), listOf(2))

        assertEquals(0.0, layout.targetX(Hand(1, 2)))
        assertEquals(listOf(Hand(1, 2), WinningTile, Hand(0, 0), Hand(0, 1)), layout.returnOrder)
    }

    /** 不含和牌張時歸位順序只有手牌，但和牌張的位置照算。 */
    @Test
    fun `leaves the winning tile out of the return order on request`() {
        val layout = ShowcaseFormationLayout(
            wingCardOrders = listOf(listOf(0, 1, 2)),
            includesWinningTile = false,
            metrics = METRICS,
        )

        assertEquals(listOf(Hand(0, 0), Hand(0, 1), Hand(0, 2)), layout.returnOrder)
        assertEquals(-1.75, layout.winningTileX)
    }

    /** 重複的 order 各佔一個順位。 */
    @Test
    fun `gives each duplicated order its own return position`() {
        val layout = ShowcaseFormationLayout(
            wingCardOrders = listOf(listOf(0, 0)),
            includesWinningTile = false,
            metrics = METRICS,
        )

        assertEquals(listOf(Hand(0, 0), Hand(0, 0)), layout.returnOrder)
    }

    /** 各種翼數的編隊都以 x = 0 置中。 */
    @Test
    fun `centres every formation`() {
        listOf(1, 2, 3).forEach { wingCount ->
            val layout = layout(*Array(wingCount) { listOf(0, 1, 2, 3) })
            val minX = layout.wingCenters.indices.minOf { layout.wingCenters[it] + layout.wingBounds(it).minX }
            val maxX = layout.wingCenters.indices.maxOf { layout.wingCenters[it] + layout.wingBounds(it).maxX }
            assertTrue(abs(minX + maxX) < 1e-9, "Expected $wingCount wings to be centred, got $minX..$maxX.")
        }
    }

    /** 以測試用尺寸建立含和牌張的編隊。 */
    private fun layout(vararg wings: List<Int>) = ShowcaseFormationLayout(
        wingCardOrders = wings.toList(),
        includesWinningTile = true,
        metrics = METRICS,
    )

    private companion object {
        /** 方便手算的整數尺寸。 */
        val METRICS = ShowcaseFormationMetrics(
            cardSpacing = 1.0,
            cardWidth = 1.0,
            winningTileWidth = 1.0,
            winningTileGap = 0.5,
            minimumWingWidth = 2.0,
            wingGap = 2.0,
        )
    }
}
