package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

/** [ActionTimeControl] 內建組合、正規化與驗證的單元測試。 */
class ActionTimeControlTest {
    /** 驗證內建組合沿用既有的五種 基本思考時間與保留思考時間數值。 */
    @Test
    fun `test built in controls use expected time combinations`() {
        assertEquals(3 to 5, ActionTimeControl.VeryShort.asPair())
        assertEquals(5 to 10, ActionTimeControl.Short.asPair())
        assertEquals(5 to 20, ActionTimeControl.Normal.asPair())
        assertEquals(60 to 0, ActionTimeControl.Long.asPair())
        assertEquals(300 to 0, ActionTimeControl.VeryLong.asPair())
    }

    /** 驗證已知數值會正規化成對應的內建 singleton。 */
    @Test
    fun `test known combination is normalized to built in control`() {
        assertSame(ActionTimeControl.Normal, ActionTimeControl.from(5, 20))
    }

    /** 驗證非內建數值會保留為自訂設定。 */
    @Test
    fun `test unknown combination is normalized to custom control`() {
        val control = assertIs<ActionTimeControl.Custom>(ActionTimeControl.from(7, 30))

        assertEquals(7, control.baseSeconds)
        assertEquals(30, control.reserveSeconds)
    }

    /** 驗證自訂設定與集中入口都拒絕無效數值。 */
    @Test
    fun `test invalid time combinations are rejected`() {
        assertFailsWith<IllegalArgumentException> { ActionTimeControl.Custom(-1, 20) }
        assertFailsWith<IllegalArgumentException> { ActionTimeControl.Custom(5, -1) }
        assertFailsWith<IllegalArgumentException> { ActionTimeControl.from(0, 0) }
    }

    /** 將控制設定轉成便於比對的基本思考時間與保留思考時間數值。 */
    private fun ActionTimeControl.asPair(): Pair<Int, Int> = baseSeconds to reserveSeconds
}
