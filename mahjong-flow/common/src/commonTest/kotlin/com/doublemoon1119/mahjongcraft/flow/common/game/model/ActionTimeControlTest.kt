package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** [ActionTimeControl] 內建組合、正規化與驗證的單元測試。 */
class ActionTimeControlTest {
    /** 驗證內建組合沿用既有的五種 基本思考時間與保留思考時間數值。 */
    @Test
    fun `test built in controls use expected time combinations`() {
        assertEquals(3.seconds to 5.seconds, ActionTimeControl.VeryShort.asPair())
        assertEquals(5.seconds to 10.seconds, ActionTimeControl.Short.asPair())
        assertEquals(5.seconds to 20.seconds, ActionTimeControl.Normal.asPair())
        assertEquals(60.seconds to 0.seconds, ActionTimeControl.Long.asPair())
        assertEquals(300.seconds to 0.seconds, ActionTimeControl.VeryLong.asPair())
    }

    /** 驗證已知數值會正規化成對應的內建 singleton。 */
    @Test
    fun `test known combination is normalized to built in control`() {
        assertSame(ActionTimeControl.Normal, ActionTimeControl.from(5.seconds, 20.seconds))
    }

    /** 驗證非內建數值會保留為自訂設定。 */
    @Test
    fun `test unknown combination is normalized to custom control`() {
        val control = assertIs<ActionTimeControl.Custom>(ActionTimeControl.from(7.seconds, 30.seconds))

        assertEquals(7.seconds, control.base)
        assertEquals(30.seconds, control.reserve)
    }

    /** 驗證自訂設定與集中入口都拒絕無效數值。 */
    @Test
    fun `test invalid time combinations are rejected`() {
        assertFailsWith<IllegalArgumentException> { ActionTimeControl.Custom(-1.seconds, 20.seconds) }
        assertFailsWith<IllegalArgumentException> { ActionTimeControl.Custom(5.seconds, -1.seconds) }
        assertFailsWith<IllegalArgumentException> { ActionTimeControl.from(0.seconds, 0.seconds) }
    }

    /** 驗證思考時間只接受有限的整數秒，避免出現設定畫面無法表達的值。 */
    @Test
    fun `test fractional or infinite time is rejected`() {
        assertFailsWith<IllegalArgumentException> { ActionTimeControl.Custom(1_500.milliseconds, 20.seconds) }
        assertFailsWith<IllegalArgumentException> { ActionTimeControl.from(5.seconds, 500.milliseconds) }
        assertFailsWith<IllegalArgumentException> { ActionTimeControl.Custom(Duration.INFINITE, 20.seconds) }
    }

    /** 將控制設定轉成便於比對的基本思考時間與保留思考時間數值。 */
    private fun ActionTimeControl.asPair(): Pair<Duration, Duration> = base to reserve
}
