package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayMeldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
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

    /** 上家鳴牌應固定在副露最左格，且使用左旋方向。 */
    @Test
    fun `meld uses left source slot and orientation`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.Pon, listOf(1, 2, 3), 3, RelativeDirectionDto.Left),
            maxWidth = 100f,
        )

        assertEquals(0f, layout.placements.first().x)
        assertEquals(3, layout.placements.first().tile)
        assertEquals(DecisionTileOrientationDto.ROTATED_LEFT, layout.placements.first().orientation)
        assertFalse(layout.placements[1].faceDown)
        assertEquals(DecisionTileOrientationDto.UPRIGHT, layout.placements[1].orientation)
    }

    /** 加槓第四張不增加橫向格位，而是疊在原來源牌上方。 */
    @Test
    fun `added kan stacks fourth tile above source tile`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.AddedKan, listOf(1, 2, 3, 4), 2, RelativeDirectionDto.Across),
            maxWidth = 100f,
        )

        assertEquals(4, layout.placements.size)
        val source = layout.placements[1]
        val added = layout.placements.last()
        assertEquals(source.x, added.x)
        assertTrue(added.y < source.y)
        assertTrue(layout.width < 4 * 18f + 3 * 2f)
    }

    /** 不完整或超過四張的加槓資料應完整保留，避免靜默丟失歷史牌面。 */
    @Test
    fun `malformed added kan keeps every saved tile`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.AddedKan, listOf(1, 2, 3, 4, 5), 1, RelativeDirectionDto.Across),
            maxWidth = 200f,
        )

        assertEquals(setOf(1, 2, 3, 4, 5), layout.placements.map { it.tile }.toSet())
        assertEquals(5, layout.placements.size)
    }

    /** 暗槓只將兩側牌顯示為牌背，且不產生橫置牌。 */
    @Test
    fun `closed kan hides only edge tiles`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.ClosedKan, listOf(1, 2, 3, 4), null, RelativeDirectionDto.Self),
            maxWidth = 100f,
        )

        assertEquals(listOf(true, false, false, true), layout.placements.map { it.faceDown })
        assertTrue(layout.placements.all { it.orientation == DecisionTileOrientationDto.UPRIGHT })
    }

    /** 副露不可拆列時應等比例縮小，而不是被換行拆開。 */
    @Test
    fun `meld scales as one group when width is limited`() {
        val layout = HistoryTileGroupLayoutCalculator.meld(
            HistoryReplayMeldDto(MeldTypeDto.OpenKan, listOf(1, 2, 3, 4), 1, RelativeDirectionDto.Right),
            maxWidth = 30f,
        )

        assertEquals(4, layout.placements.size)
        assertTrue(layout.width <= 30f)
        assertTrue(layout.placements.zipWithNext().all { (left, right) -> right.x >= left.x + left.width })
    }
}
