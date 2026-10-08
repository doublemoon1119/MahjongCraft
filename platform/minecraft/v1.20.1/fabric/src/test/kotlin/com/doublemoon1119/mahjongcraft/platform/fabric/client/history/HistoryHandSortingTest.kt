package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTileOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證歷史手牌排序偏好的隔離性與牌面排序結果。 */
class HistoryHandSortingTest {
    /** 每位玩家的切換狀態彼此獨立，且預設使用規則排序。 */
    @Test
    fun `sorting preference is independent per player`() {
        val sorting = HistoryHandSorting()

        assertTrue(sorting.isSorted("match-a", "player-a"))
        assertTrue(sorting.isSorted("match-a", "player-b"))
        assertFalse(sorting.toggle("match-a", "player-a"))
        assertFalse(sorting.isSorted("match-a", "player-a"))
        assertTrue(sorting.isSorted("match-a", "player-b"))
    }

    /** 不同對局不會共用同一玩家的排序偏好。 */
    @Test
    fun `sorting preference is isolated by match`() {
        val sorting = HistoryHandSorting()

        sorting.toggle("match-a", "player-a")

        assertFalse(sorting.isSorted("match-a", "player-a"))
        assertTrue(sorting.isSorted("match-b", "player-a"))
    }

    /** 相同牌面仍保留不同實體索引的原始順序。 */
    @Test
    fun `equal tile values retain physical order`() {
        val player = player(handTiles = listOf(1, 0, 2))
        val catalog = listOf(tile(5), tile(5), tile(1))

        assertEquals(listOf(2, 1, 0), HistoryHandSorting().orderedTiles(player, catalog, RiichiTileOrder))
    }

    /** 切換回規則排序時仍可重新取得原本的保存順序。 */
    @Test
    fun `toggle restores default preference`() {
        val sorting = HistoryHandSorting()

        assertFalse(sorting.toggle("match-a", "player-a"))
        assertTrue(sorting.toggle("match-a", "player-a"))
        assertTrue(sorting.isSorted("match-a", "player-a"))
    }

    /** 最近摸牌不會被混入立牌排序結果。 */
    @Test
    fun `last drawn tile stays separate`() {
        val player = player(handTiles = listOf(2, 1), lastDrawn = 0)
        val catalog = listOf(tile(9), tile(1), tile(2))

        assertEquals(listOf(1, 2), HistoryHandSorting().orderedTiles(player, catalog, RiichiTileOrder))
    }

    /** 沒有排序策略時完整保留來源立牌順序。 */
    @Test
    fun `null order preserves original standing order`() {
        val player = player(handTiles = listOf(2, 0, 1), lastDrawn = 3)

        assertEquals(listOf(2, 0, 1), HistoryHandSorting().orderedTiles(player, emptyList(), null))
    }

    /**
     * 建立排序測試使用的玩家資料。
     * @param handTiles 保存順序的立牌索引。
     * @param lastDrawn 獨立摸入牌索引。
     * @return 無副露與牌河的玩家資料。
     */
    private fun player(handTiles: List<Int>, lastDrawn: Int? = null) = HistoryReplayPlayerStateDto(
        initialSeatIndex = 0,
        handTiles = handTiles,
        melds = emptyList(),
        lastDrawn = lastDrawn,
        discards = emptyList(),
        score = 25000,
        seatWind = WindDto.EAST,
        playerRuleState = null,
        setAsideTiles = emptyList(),
    )

    /**
     * 建立指定數值的萬子牌面。
     * @param value 牌面數值。
     * @return 測試牌面。
     */
    private fun tile(value: Int) = TileDto.Numeric(SuitDto.CHARACTER, value)
}
