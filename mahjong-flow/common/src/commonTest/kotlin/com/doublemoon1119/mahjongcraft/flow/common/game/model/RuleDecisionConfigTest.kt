package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 驗證規則選擇時間設定的合法範圍。 */
class RuleDecisionConfigTest {
    /** 驗證正的整數秒可以建立設定。 */
    @Test
    fun `test positive whole seconds are accepted`() {
        assertEquals(30.seconds, RuleDecisionConfig(30.seconds).base)
    }

    /** 驗證零、負數、非整數秒與無限時間都會被拒絕。 */
    @Test
    fun `test zero negative fractional or infinite time is rejected`() {
        assertFailsWith<IllegalArgumentException> { RuleDecisionConfig(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { RuleDecisionConfig((-1).seconds) }
        assertFailsWith<IllegalArgumentException> { RuleDecisionConfig(2_500.milliseconds) }
        assertFailsWith<IllegalArgumentException> { RuleDecisionConfig(Duration.INFINITE) }
    }
}
