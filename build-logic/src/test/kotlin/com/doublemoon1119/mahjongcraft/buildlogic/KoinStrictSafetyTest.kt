package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 Koin 完整依賴檢查的開關：預設只在 CI 執行，明確指定時以指定為準。 */
class KoinStrictSafetyTest {
    /** 沒有指定時，只在 CI 中執行。 */
    @Test
    fun `defaults to running only on ci`() {
        assertFalse(koinStrictSafetyEnabled(property = null, ci = null))
        assertFalse(koinStrictSafetyEnabled(property = null, ci = "false"))
        assertTrue(koinStrictSafetyEnabled(property = null, ci = "true"))
    }

    /** 明確指定時蓋過 CI 的預設。 */
    @Test
    fun `explicit property overrides the ci default`() {
        assertTrue(koinStrictSafetyEnabled(property = "true", ci = null))
        assertFalse(koinStrictSafetyEnabled(property = "false", ci = "true"))
    }

    /** 只接受 true 與 false。 */
    @Test
    fun `rejects other values`() {
        assertFailsWith<GradleException> { koinStrictSafetyEnabled(property = "yes", ci = null) }
    }
}
