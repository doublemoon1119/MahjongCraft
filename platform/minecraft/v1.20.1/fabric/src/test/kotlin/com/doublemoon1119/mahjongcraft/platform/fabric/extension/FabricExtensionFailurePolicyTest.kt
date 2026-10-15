package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [FabricExtensionFailurePolicy] 的隔離範圍測試。 */
class FabricExtensionFailurePolicyTest {
    /** 一般例外與類別連結錯誤可以隔離。 */
    @Test
    fun `ordinary exceptions and linkage errors are isolated`() {
        val policy = FabricExtensionFailurePolicy()

        assertTrue(policy.isIsolatable(IllegalStateException("extension failure")))
        assertTrue(policy.isIsolatable(NoClassDefFoundError("com/example/MissingDependency")))
        assertTrue(policy.isIsolatable(NoSuchMethodError("com.example.Changed.method()")))
        assertTrue(policy.isIsolatable(AbstractMethodError("com.example.Provider.resolve")))
    }

    /** 協程取消與代表執行環境已不可靠的錯誤不得隔離。 */
    @Test
    fun `cancellation and virtual machine errors are not isolated`() {
        val policy = FabricExtensionFailurePolicy()

        assertFalse(policy.isIsolatable(CancellationException("cancelled")))
        assertFalse(policy.isIsolatable(OutOfMemoryError("out of memory")))
        assertFalse(policy.isIsolatable(StackOverflowError("stack overflow")))
        assertFalse(policy.isIsolatable(AssertionError("assertion")))
    }
}
