package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayMeldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTileOrientationDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證歷史牌面群組的換行、鳴牌方向、暗槓牌背與加槓疊牌幾何。 */
class HistoryTileGroupLayoutTest {
    /** 手牌過寬時換行，但和牌張仍保留在牌組末端並與立牌分隔。 */
    @Test
    fun `hand wraps without losing winning tile separation`() {
        val layout = HistoryTileGroupLayoutCalculator.hand(
            tiles = listOf(1, 2, 3, 4, 5),
            winningTile = 6,
            maxWidth = 50f,
        )

        assertEquals(listOf(1, 2, 3, 4, 5, 6), layout.placements.map { it.tile })
        assertEquals(3, layout.placements.map { it.y }.distinct().size)
        assertTrue(layout.placements.last().x >= 0f)
        assertTrue(layout.width <= 50f)
        val finalRow = layout.placements.takeLast(2)
        assertTrue(finalRow[1].x - (finalRow[0].x + finalRow[0].width) > 2f)
    }

    /** 橫置牌與直立牌共用底線，且窄於牌寬時整列會縮放。 */
    @Test
    fun `hand aligns sideways tiles and scales below tile width`() {
        val aligned = HistoryTileGroupLayoutCalculator.hand(
            tiles = listOf(1, 2),
            maxWidth = 50f,
            sidewaysTiles = setOf(2),
        )

        assertEquals(DecisionTileOrientationDto.ROTATED_RIGHT, aligned.placements.last().orientation)
        assertEquals(
            aligned.placements.first().y + aligned.placements.first().height,
            aligned.placements.last().y + aligned.placements.last().height,
        )

        val scaled = HistoryTileGroupLayoutCalculator.hand(
            tiles = listOf(1, 2),
            maxWidth = 10f,
            sidewaysTiles = setOf(2),
        )

        assertTrue(scaled.width <= 10f)
    }

    /** 牌譜的副露全部直立，依保存順序排列，不因鳴牌來源改變順序或方向。 */
    @Test
    fun `meld keeps saved order with every tile upright`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.Pon, listOf(1, 2, 3), 3, RelativeDirectionDto.Left, emptySet(), emptySet()),
            maxWidth = 100f,
        )

        assertEquals(listOf(1, 2, 3), layout.placements.map { it.tile })
        assertTrue(layout.placements.all { it.orientation == DecisionTileOrientationDto.UPRIGHT })
        assertTrue(layout.placements.all { it.y == 0f })
        assertFalse(layout.placements.any { it.faceDown })
    }

    /** 加槓的四張排成一列，補上的牌保留自己的牌面狀態。 */
    @Test
    fun `added kan lays four tiles in a row`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.AddedKan, listOf(1, 2, 3, 4), 2, RelativeDirectionDto.Across, setOf(4), emptySet()),
            maxWidth = 100f,
        )

        assertEquals(listOf(1, 2, 3, 4), layout.placements.map { it.tile })
        assertTrue(layout.placements.zipWithNext().all { (left, right) -> right.x > left.x && right.y == left.y })
        assertTrue(layout.placements.last().faceDown)
        assertEquals(4 * 18f + 3 * 2f, layout.width)
    }

    /** 不完整或超過四張的加槓資料應完整保留，避免靜默丟失歷史牌面。 */
    @Test
    fun `malformed added kan keeps every saved tile`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.AddedKan, listOf(1, 2, 3, 4, 5), 1, RelativeDirectionDto.Across, emptySet(), emptySet()),
            maxWidth = 200f,
        )

        assertEquals(setOf(1, 2, 3, 4, 5), layout.placements.map { it.tile }.toSet())
        assertEquals(5, layout.placements.size)
    }

    /** 暗槓只將兩側牌顯示為牌背，且不產生橫置牌。 */
    @Test
    fun `closed kan hides only edge tiles`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.ClosedKan, listOf(1, 2, 3, 4), null, RelativeDirectionDto.Self, setOf(1, 4), emptySet()),
            maxWidth = 100f,
        )

        assertEquals(listOf(true, false, false, true), layout.placements.map { it.faceDown })
        assertTrue(layout.placements.all { it.orientation == DecisionTileOrientationDto.UPRIGHT })
    }

    /** 副露不可拆列時應等比例縮小，而不是被換行拆開。 */
    @Test
    fun `meld scales as one group when width is limited`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.OpenKan, listOf(1, 2, 3, 4), 1, RelativeDirectionDto.Right, emptySet(), emptySet()),
            maxWidth = 30f,
        )

        assertEquals(4, layout.placements.size)
        assertTrue(layout.width <= 30f)
        assertTrue(layout.placements.zipWithNext().all { (left, right) -> right.x >= left.x + left.width })
    }
}
