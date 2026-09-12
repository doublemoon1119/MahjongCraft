package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSubmissionResultKindDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Server final submission 單一結果出口的測試。 */
class DecisionSubmissionResultResolverTest {
    /** 接受、拒絕與 stale 都應保持原結果。 */
    @Test
    fun `test every normal result is preserved`() = runTest {
        PlayerDecisionSubmissionResultKindDto.entries.forEach { expected ->
            assertEquals(expected, resolveFinalDecisionSubmission { expected })
        }
    }

    /** 非取消例外一律轉成 REJECTED，避免 final submission 遺失 ACK。 */
    @Test
    fun `test processing failure becomes rejected`() = runTest {
        var reported: Throwable? = null
        val result = resolveFinalDecisionSubmission(onFailure = { reported = it }) {
            error("test failure")
        }

        assertEquals(PlayerDecisionSubmissionResultKindDto.REJECTED, result)
        assertEquals("test failure", reported?.message)
    }

    /** Coroutine 取消必須繼續向外傳播，不能偽裝成一般玩家操作遭拒。 */
    @Test
    fun `test cancellation is rethrown`() = runTest {
        assertFailsWith<CancellationException> {
            resolveFinalDecisionSubmission { throw CancellationException("test cancellation") }
        }
    }
}
