package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultKindDto
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
            assertEquals(expected, resolveFinalDecisionSubmission(failurePolicy = ExtensionFailurePolicy.Default) { expected })
        }
    }

    /** 非取消例外一律轉成 REJECTED，避免 final submission 遺失 ACK。 */
    @Test
    fun `test processing failure becomes rejected`() = runTest {
        var reported: Throwable? = null
        val result = resolveFinalDecisionSubmission(failurePolicy = ExtensionFailurePolicy.Default, onFailure = { reported = it }) {
            error("test failure")
        }

        assertEquals(PlayerDecisionSubmissionResultKindDto.REJECTED, result)
        assertEquals("test failure", reported?.message)
    }

    /** Coroutine 取消必須繼續向外傳播，不能偽裝成一般玩家操作遭拒。 */
    @Test
    fun `test cancellation is rethrown`() = runTest {
        assertFailsWith<CancellationException> {
            resolveFinalDecisionSubmission(failurePolicy = ExtensionFailurePolicy.Default) { throw CancellationException("test cancellation") }
        }
    }

    /** 類別連結錯誤由 Fabric 判斷規則隔離成 REJECTED；記憶體不足等嚴重錯誤原樣往外傳。 */
    @Test
    fun `test linkage errors are rejected and fatal errors are rethrown`() = runTest {
        val policy = FabricExtensionFailurePolicy()

        val result = resolveFinalDecisionSubmission(failurePolicy = policy) { throw NoClassDefFoundError("com/example/Missing") }

        assertEquals(PlayerDecisionSubmissionResultKindDto.REJECTED, result)
        assertFailsWith<OutOfMemoryError> {
            resolveFinalDecisionSubmission(failurePolicy = policy) { throw OutOfMemoryError("test fatal error") }
        }
    }
}
