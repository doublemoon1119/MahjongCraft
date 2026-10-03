package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證 Replay 原文字串 parser 的詞法與資源邊界。 */
class BoundedReplayJsonParserTest {
    /** 引號內的括號與跳脫字元不得改變深度。 */
    @Test
    fun `quoted brackets and escapes are ignored`() = runTest {
        val result = parseBoundedReplayJson("{\"value\":\"[}\\\"{]\"}", ReplayJsonParseLimits(maximumDepth = 1))
        assertIs<ReplayReadResult.Success<*>>(result)
    }

    /** 超過 JSON 巢狀深度時回傳資源限制錯誤。 */
    @Test
    fun `depth limit is enforced`() = runTest {
        val result = parseBoundedReplayJson("[[[0]]]", ReplayJsonParseLimits(maximumDepth = 2))
        assertEquals(ReplayReadError.LIMIT_EXCEEDED, assertIs<ReplayReadResult.Failure>(result).error)
    }

    /** UTF-8 位元組上限以原始編碼大小計算。 */
    @Test
    fun `utf8 byte limit is enforced`() = runTest {
        val result = parseBoundedReplayJson("{\"值\":\"牌\"}", ReplayJsonParseLimits(maximumUtf8Bytes = 8))
        assertEquals(ReplayReadError.LIMIT_EXCEEDED, assertIs<ReplayReadResult.Failure>(result).error)
    }

    /** 近似詞元數量上限應拒絕過大的標點序列。 */
    @Test
    fun `token limit is enforced`() = runTest {
        val result = parseBoundedReplayJson("{" + "\"a\":0,".repeat(8) + "\"z\":0}", ReplayJsonParseLimits(maximumTokens = 3))
        assertEquals(ReplayReadError.LIMIT_EXCEEDED, assertIs<ReplayReadResult.Failure>(result).error)
    }

    /** JSON 語法錯誤、尾端資料及非物件根節點均拒絕。 */
    @Test
    fun `malformed trailing and nonobject roots are invalid`() = runTest {
        for (source in listOf("{", "{}{}", "[]")) {
            val result = parseBoundedReplayJson(source)
            assertEquals(ReplayReadError.INVALID_DOCUMENT, assertIs<ReplayReadResult.Failure>(result).error)
        }
    }

    /** 已取消的 coroutine 不應被轉換成一般 Replay 失敗。 */
    @Test
    fun `cancellation propagates`() = runTest {
        val deferred = async {
            parseBoundedReplayJson("{" + "\"a\":0,".repeat(512) + "\"z\":0}")
        }
        deferred.cancel()
        try {
            deferred.await()
            error("Expected cancellation")
        } catch (_: CancellationException) {
            // 取消須傳播，不轉換為一般讀取失敗。
        }
    }
}
