package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證篩選草稿在切換全部範圍時清除不適用名次欄位。 */
class HistoryFilterDraftTest {
    /** ALL 範圍不得保留名次文字或其欄位錯誤。 */
    @Test
    fun `all scope clears rank draft`() {
        val draft = HistoryFilterDraft()
        draft.input = HistoryBrowseFilterInput(ownRankMin = "2", ownRankMax = "4")
        draft.errors = mapOf(HistoryBrowseFilterField.MIN_RANK to HistoryBrowseFilterError.INVALID_RANK)
        draft.setScope(HistoryQueryScopeDto.ALL)
        assertEquals("", draft.input.ownRankMin)
        assertEquals("", draft.input.ownRankMax)
        assertEquals(emptyMap(), draft.errors)
    }
}
