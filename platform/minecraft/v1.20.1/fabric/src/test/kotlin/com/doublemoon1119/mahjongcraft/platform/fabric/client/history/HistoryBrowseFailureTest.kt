package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證伺服器公開錯誤與瀏覽失敗狀態的一致對應。 */
class HistoryBrowseFailureTest {
    /** 所有線路錯誤都保留原語意，伺服器逾時與客戶端逾時分開。 */
    @Test
    fun `test every server error maps to a distinct browse failure`() {
        val expected = mapOf(
            HistoryQueryErrorCodeDto.QUERY_DISABLED to HistoryBrowseFailure.QUERY_DISABLED,
            HistoryQueryErrorCodeDto.ACCESS_DENIED to HistoryBrowseFailure.ACCESS_DENIED,
            HistoryQueryErrorCodeDto.INVALID_REQUEST to HistoryBrowseFailure.INVALID_REQUEST,
            HistoryQueryErrorCodeDto.NOT_AVAILABLE to HistoryBrowseFailure.NOT_AVAILABLE,
            HistoryQueryErrorCodeDto.BUSY to HistoryBrowseFailure.BUSY,
            HistoryQueryErrorCodeDto.RATE_LIMITED to HistoryBrowseFailure.RATE_LIMITED,
            HistoryQueryErrorCodeDto.TIMEOUT to HistoryBrowseFailure.SERVER_TIMEOUT,
            HistoryQueryErrorCodeDto.DISCONNECTED to HistoryBrowseFailure.DISCONNECTED,
            HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE to HistoryBrowseFailure.CONTENT_TOO_LARGE,
        )
        assertEquals(HistoryQueryErrorCodeDto.entries.toSet(), expected.keys)
        expected.forEach { (code, failure) -> assertEquals(failure, code.toBrowseFailure()) }
    }
}
