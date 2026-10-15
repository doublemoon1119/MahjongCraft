package com.doublemoon1119.mahjongcraft.flow.common.error

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [ExtensionFailurePolicy.Default] 的隔離範圍測試。 */
class ExtensionFailurePolicyTest {
    /** 一般例外可以隔離；協程取消與錯誤不得隔離。 */
    @Test
    fun `default policy isolates ordinary exceptions only`() {
        val policy = ExtensionFailurePolicy.Default

        assertTrue(policy.isIsolatable(IllegalStateException("extension failure")))
        assertFalse(policy.isIsolatable(CancellationException("cancelled")))
        assertFalse(policy.isIsolatable(Error("serious error")))
    }
}
