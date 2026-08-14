package com.doublemoon1119.mahjongcraft.logic.table.opening

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證權威骰子結果的值域、總和與輸入副本。 */
class DiceRollResultTest {
    /** 驗證個別點數與總和會完整保留。 */
    @Test
    fun `valid dice values preserve each die and total`() {
        val result = DiceRollResult.of(listOf(2, 5))

        assertEquals(listOf(2, 5), result.values)
        assertEquals(7, result.total)
    }

    /** 驗證呼叫端修改原始清單不會改變已建立的權威結果。 */
    @Test
    fun `dice result copies caller owned values`() {
        val values = mutableListOf(1, 6)
        val result = DiceRollResult.of(values)

        values[0] = 4

        assertEquals(listOf(1, 6), result.values)
        assertEquals(7, result.total)
    }

    /** 驗證空結果與超出六面骰值域的點數會被拒絕。 */
    @Test
    fun `invalid dice values are rejected`() {
        assertFailsWith<IllegalArgumentException> { DiceRollResult.of(emptyList()) }
        assertFailsWith<IllegalArgumentException> { DiceRollResult.of(listOf(0, 1)) }
        assertFailsWith<IllegalArgumentException> { DiceRollResult.of(listOf(1, 7)) }
    }
}
