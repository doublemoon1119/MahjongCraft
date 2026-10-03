package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證局內事件游標只使用已確認的交易索引。 */
class HistoryRoundNavigationTest {
    /** 初始位置只可在明確取得交易零時前進。 */
    @Test
    fun `initial position advances only when transaction zero is known`() {
        val empty = HistoryBrowseRoundState(1)
        assertNull(HistoryRoundNavigation.nextPosition(empty, HistoryRoundPositionDto.Initial, 1))
        val recorded = HistoryRoundNavigation.recordEvents(empty, events(listOf(0, 1), 2))
        assertEquals(HistoryRoundPositionDto.AfterTransaction(0), HistoryRoundNavigation.nextPosition(recorded, HistoryRoundPositionDto.Initial, 1))
        assertEquals(HistoryRoundPositionDto.Initial, HistoryRoundNavigation.nextPosition(recorded, HistoryRoundPositionDto.AfterTransaction(0), -1))
    }

    /** 事件頁記錄應保存交易索引與終局索引。 */
    @Test
    fun `recording terminal page stores indices`() {
        val state = HistoryRoundNavigation.recordEvents(HistoryBrowseRoundState(1), events(listOf(2, 3), null))
        assertEquals(setOf(2, 3), state.knownTransactionIndices)
        assertEquals(3, state.lastTransactionIndex)
        assertNull(HistoryRoundNavigation.nextPosition(state, HistoryRoundPositionDto.AfterTransaction(3), 1))
    }

    /** 建立不含牌引用的導航事件頁。
     * @param indices 已確認的交易索引。
     * @param next 下一頁起點；null 表示終端頁。
     * @return 導航測試使用的唯讀資料。
     */
    private fun events(indices: List<Int>, next: Int?): HistoryRoundEventsDto = HistoryRoundEventsDto(
        identity = HistoryReplayIdentityDto("match", "table", emptyList()),
        roundNumber = 1,
        transactions = indices.map { HistoryReplayTransactionDto(it, it.toLong(), false, 0, emptyList()) },
        nextTransactionIndex = next,
        tileCatalog = emptyList(),
    )
}
