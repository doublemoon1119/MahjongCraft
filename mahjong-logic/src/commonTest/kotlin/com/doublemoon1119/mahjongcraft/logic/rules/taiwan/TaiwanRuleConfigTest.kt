package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 驗證 [TaiwanRuleConfig] 的設定屬性。
 */
class TaiwanRuleConfigTest {

    /**
     * 測試預設建構的 [TaiwanRuleConfig] 是否具有正確的初始值。
     */
    @Test
    fun `test default config values`() {
        val config = TaiwanRuleConfig()

        assertEquals(16, config.initialHandSize)
        assertEquals(16, config.deadTileCount)
        assertEquals(4, config.minPlayers)
        assertEquals(4, config.maxPlayers)
        assertEquals(0, config.minimumWinConstraint)
        assertEquals(true, config.useFlowerTiles)
        assertEquals(
            MultiRonPolicy(doubleRonResolution = RonResolution.NEAREST_WINNER, tripleRonResolution = RonResolution.NEAREST_WINNER),
            config.multiRonPolicy,
        )
    }
}
