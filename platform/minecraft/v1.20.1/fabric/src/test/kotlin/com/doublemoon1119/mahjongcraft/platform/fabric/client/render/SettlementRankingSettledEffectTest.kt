package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證排名變動效果只在設定的結算時間窗內啟用。 */
class SettlementRankingSettledEffectTest {
    /** 排名未改變時不顯示結算效果。 */
    @Test
    fun `test unchanged rank never receives settled effect`() {
        assertFalse(SettlementRankingSettledEffect.resolve(105.0, 100.0, rankChanged = false).active)
    }

    /** 排名改變時只在結算時間窗內顯示效果。 */
    @Test
    fun `test changed rank receives effect only inside settled window`() {
        assertFalse(SettlementRankingSettledEffect.resolve(99.0, 100.0, rankChanged = true).active)
        assertTrue(SettlementRankingSettledEffect.resolve(105.0, 100.0, rankChanged = true).active)
        assertFalse(SettlementRankingSettledEffect.resolve(114.0, 100.0, rankChanged = true).active)
    }

    /** 結算效果接近時間窗邊界時回到中性尺寸。 */
    @Test
    fun `test settled effect returns to neutral state at its boundary`() {
        val beforeEnd = SettlementRankingSettledEffect.resolve(113.999, 100.0, rankChanged = true)

        assertTrue(beforeEnd.active)
        assertEquals(1f, beforeEnd.rowScale, absoluteTolerance = 0.001f)
        assertEquals(1f, beforeEnd.rankScale, absoluteTolerance = 0.001f)
    }
}
