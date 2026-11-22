package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證局內導航只使用伺服器回報的交易總數。 */
class HistoryRoundNavigationTest {
    /** 尚未取得交易總數時初始位置不能前進；取得後可以前進到第一筆交易並退回初始位置。 */
    @Test
    fun `initial position advances only after the transaction count is known`() {
        val empty = HistoryBrowseRoundState(1)
        assertNull(HistoryRoundNavigation.nextPosition(empty, HistoryRoundPositionDto.Initial, 1))
        val recorded = HistoryRoundNavigation.recordEvents(empty, events(listOf(0, 1), 2))
        assertEquals(HistoryRoundPositionDto.AfterTransaction(0), HistoryRoundNavigation.nextPosition(recorded, HistoryRoundPositionDto.Initial, 1))
        assertEquals(HistoryRoundPositionDto.Initial, HistoryRoundNavigation.nextPosition(recorded, HistoryRoundPositionDto.AfterTransaction(0), -1))
    }

    /** 事件頁記錄保存交易總數與最後一筆的索引；最後一筆之後不能前進。 */
    @Test
    fun `recording a page stores the transaction count`() {
        val state = HistoryRoundNavigation.recordEvents(HistoryBrowseRoundState(1), events(listOf(2, 3), null))
        assertEquals(4, state.transactionCount)
        assertEquals(3, state.lastTransactionIndex)
        assertNull(HistoryRoundNavigation.nextPosition(state, HistoryRoundPositionDto.AfterTransaction(3), 1))
    }

    /** 前面頁面看過的交易總數讓後面頁面的交易也能直接導航，不需要先讀到那一頁。 */
    @Test
    fun `the count covers transactions on pages not read yet`() {
        val state = HistoryRoundNavigation.recordEvents(HistoryBrowseRoundState(1), events(listOf(0, 1), next = 2, transactionCount = 45))
        assertEquals(HistoryRoundPositionDto.AfterTransaction(2), HistoryRoundNavigation.nextPosition(state, HistoryRoundPositionDto.AfterTransaction(1), 1))
        assertEquals(44, state.lastTransactionIndex)
        assertTrue(state.hasTransaction(44))
        assertFalse(state.hasTransaction(45))
        assertFalse(state.hasTransaction(-1))
    }

    /** 跳到最後一頁時，起點從 0 開始每頁固定筆數，往前翻頁仍逐頁回到第一頁。 */
    @Test
    fun `last page starts step back page by page`() {
        assertEquals(listOf(0, 20, 40), HistoryRoundNavigation.lastPageStarts(transactionCount = 45, pageSize = 20))
        assertEquals(listOf(0, 20), HistoryRoundNavigation.lastPageStarts(transactionCount = 40, pageSize = 20))
        assertEquals(listOf(0), HistoryRoundNavigation.lastPageStarts(transactionCount = 3, pageSize = 20))
        assertEquals(listOf(0), HistoryRoundNavigation.lastPageStarts(transactionCount = 0, pageSize = 20))
    }

    /**
     * 建立不含牌引用的導航事件頁。
     *
     * @param indices 已確認的交易索引。
     * @param next 下一頁起點；null 表示終端頁。
     * @param transactionCount 本局交易總數；預設為剛好涵蓋本頁與下一頁起點。
     * @return 導航測試使用的唯讀資料。
     */
    private fun events(
        indices: List<Int>,
        next: Int?,
        transactionCount: Int = next?.plus(1) ?: (indices.lastOrNull()?.plus(1) ?: 0),
    ): HistoryRoundEventsDto = HistoryRoundEventsDto(
        identity = HistoryReplayIdentityDto("match", "table", emptyList()),
        roundNumber = 1,
        transactions = indices.map { HistoryReplayTransactionDto(it, it.toLong(), false, 0, emptyList()) },
        nextTransactionIndex = next,
        transactionCount = transactionCount,
        tileCatalog = emptyList(),
    )
}
