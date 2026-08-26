package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettlementRankingSettledEffectTest {
    @Test
    fun `test unchanged rank never receives settled effect`() {
        assertFalse(SettlementRankingSettledEffect.resolve(105.0, 100.0, rankChanged = false).active)
    }

    @Test
    fun `test changed rank receives effect only inside settled window`() {
        assertFalse(SettlementRankingSettledEffect.resolve(99.0, 100.0, rankChanged = true).active)
        assertTrue(SettlementRankingSettledEffect.resolve(105.0, 100.0, rankChanged = true).active)
        assertFalse(SettlementRankingSettledEffect.resolve(114.0, 100.0, rankChanged = true).active)
    }

    @Test
    fun `test settled effect returns to neutral state at its boundary`() {
        val beforeEnd = SettlementRankingSettledEffect.resolve(113.999, 100.0, rankChanged = true)

        assertTrue(beforeEnd.active)
        assertEquals(1f, beforeEnd.rowScale, absoluteTolerance = 0.001f)
        assertEquals(1f, beforeEnd.rankScale, absoluteTolerance = 0.001f)
    }
}
