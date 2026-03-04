package com.doublemoon1119.mahjongcraft.domain.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.config.GameLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對日本麻將規則配置 [RiichiRuleConfig] 進行測試。
 */
class RiichiRuleConfigTest {

    /**
     * 模擬日本麻將配置中的遊戲長度。
     */
    private class RiichiGameLengthMock(
        override val totalRounds: Int = 8,
        override val name: String = "HALF_CHAN"
    ) : GameLength

    /**
     * 模擬日本麻將配置實作類別。
     *
     * @property redDoraCount 指定赤寶牌數量。
     * @property allowOpenTanyao 是否允許食斷。
     * @property useLocalYaku 是否啟用古役。
     * @property scoreConfig 積分配置實作，預設使用 [RiichiScoreConfig]。
     * @property gameLength 遊戲長度配置，預設使用 [RiichiGameLengthMock]。
     */
    private class RiichiMock(
        override val redDoraCount: Int = 3,
        override val allowOpenTanyao: Boolean = true,
        override val useLocalYaku: Boolean = false,
        override val scoreConfig: RiichiScoreConfig = RiichiScoreConfig(),
        override val gameLength: GameLength = RiichiGameLengthMock()
    ) : RiichiRuleConfig {
        /** 固定日麻標準初始手牌張數 13。 */
        override val initialHandSize = 13

        /** 測試用空牌組。 */
        override val tileSet = emptyList<Tile>()

        /** 固定日麻王牌張數 14。 */
        override val deadTileCount = 14

        /** 日麻通常為一翻縛。 */
        override val minimumWinConstraint = 1
    }

    /**
     * 測試日本麻將特有屬性、積分規則與胡牌限制的存取與正確性。
     */
    @Test
    fun `test riichi specific configuration`() {
        // 初始化具備特定規則開關的模擬配置
        val config = RiichiMock(
            redDoraCount = 4,
            allowOpenTanyao = true,
            useLocalYaku = true
        )

        // 驗證繼承自基礎介面的通用屬性
        assertEquals(13, config.initialHandSize)
        assertEquals(1, config.minimumWinConstraint, "Riichi should have 1-han constraint")

        // 驗證日麻特有的規則開關
        assertEquals(4, config.redDoraCount)
        assertTrue(config.allowOpenTanyao, "Should support open tanyao configuration")
        assertTrue(config.useLocalYaku, "Should support local yaku configuration")

        // 驗證積分配置（包含新增的 1 位必要點數）
        assertEquals(25000, config.scoreConfig.initialScore)
        assertEquals(0, config.scoreConfig.bustThreshold)
        assertEquals(30000, config.scoreConfig.minPointsToWin, "Default min points to win should be 30000")

        // 驗證遊戲長度配置
        assertEquals(8, config.gameLength.totalRounds)
        assertEquals("HALF_CHAN", config.gameLength.name)
    }
}