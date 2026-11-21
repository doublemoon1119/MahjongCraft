package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** 驗證壓力測試各桌的對局種子由基準種子與建立順序決定。 */
class StressSeedTest {
    /** 同一個基準種子與建立順序得到相同的對局種子，重跑時各桌的座位、牌山與骰子相同。 */
    @Test
    fun `same base seed and table order give the same match seed`() {
        assertEquals(StressSeed(42).matchSeed(3), StressSeed(42).matchSeed(3))
    }

    /** 不同的桌或不同的基準種子得到不同的對局種子。 */
    @Test
    fun `different tables and base seeds give different match seeds`() {
        val seed = StressSeed(42)

        assertNotEquals(seed.matchSeed(0), seed.matchSeed(1))
        assertNotEquals(seed.matchSeed(0), StressSeed(43).matchSeed(0))
    }

    /** 建立順序從 0 起算，不接受負數。 */
    @Test
    fun `negative table order is rejected`() {
        assertFailsWith<IllegalArgumentException> { StressSeed(42).matchSeed(-1) }
    }
}
