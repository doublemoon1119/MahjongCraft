package com.doublemoon1119.mahjongcraft.domain.taiwan

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.config.GameLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 針對台灣麻將規則配置 [TaiwanRuleConfig] 進行測試。
 */
class TaiwanRuleConfigTest {

    /**
     * 模擬台灣麻將配置中的遊戲長度。
     */
    private class TaiwanGameLengthMock(
        override val totalRounds: Int = 16,
        override val name: String = "ONE_SHOE"
    ) : GameLength

    /**
     * 模擬台灣麻將配置實作類別。
     *
     * @property useFlowerTiles 指定是否使用花牌。
     * @property scoreConfig 積分配置實作，預設底分為 30，台分為 10。
     * @property gameLength 遊戲長度配置，預設使用 [TaiwanGameLengthMock]。
     */
    private class TaiwanMock(
        override val useFlowerTiles: Boolean = true,
        override val scoreConfig: TaiwanScoreConfig = TaiwanScoreConfig(baseScore = 30, pointPerTai = 10),
        override val gameLength: GameLength = TaiwanGameLengthMock()
    ) : TaiwanRuleConfig {
        /** 固定台麻標準初始手牌張數 16。 */
        override val initialHandSize = 16

        /** 測試用空牌組。 */
        override val tileSet = emptyList<Tile>()

        /** 固定台麻王牌/保留牌張數 8。 */
        override val deadTileCount = 8

        /** 台灣麻將通常無起胡台數限制。 */
        override val minimumWinConstraint = 0
    }

    /**
     * 測試台灣麻將特有屬性（如花牌開關）、底台積分配置與對局長度的存取與正確性。
     */
    @Test
    fun `test taiwan specific configuration`() {
        // 初始化啟用花牌且底 30 台 10 的模擬配置
        val config = TaiwanMock(useFlowerTiles = true)

        // 驗證基礎屬性
        assertEquals(16, config.initialHandSize)
        assertEquals(0, config.minimumWinConstraint, "Taiwan mahjong typically has no minimum Tai constraint")

        // 驗證台麻特有的花牌啟用法則
        assertTrue(config.useFlowerTiles, "Taiwan rules should allow toggling flower tiles")

        // 驗證台麻特有的積分配置（底與台）
        assertEquals(30, config.scoreConfig.baseScore, "Base score should be 30")
        assertEquals(10, config.scoreConfig.pointPerTai, "Point per Tai should be 10")
        assertNull(config.scoreConfig.bustThreshold, "Taiwan mahjong should not have a bust threshold by default")

        // 驗證遊戲長度配置
        assertEquals(16, config.gameLength.totalRounds, "One shoe of Taiwan mahjong should have 16 rounds")
        assertEquals("ONE_SHOE", config.gameLength.name)
    }
}