package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對日本麻將規則配置 [RiichiRuleConfig] 進行測試。
 */
class RiichiRuleConfigTest {

    /**
     * 模擬日本麻將配置實作類別。
     *
     * @property redDoraCount 指定赤寶牌數量。
     */
    private class RiichiMock(
        override val redDoraCount: Int = 3
    ) : RiichiRuleConfig {
        /** 固定日麻標準初始手牌張數 13 */
        override val initialHandSize = 13
        /** 測試用空牌組 */
        override val tileSet = emptyList<Tile>()
        /** 固定日麻王牌張數 14 */
        override val deadTileCount = 14
    }

    /**
     * 測試日本麻將特有屬性（如赤寶牌數量）的存取與正確性。
     */
    @Test
    fun `test riichi specific configuration`() {
        // 初始化具備 3 張赤寶牌的模擬配置
        val config = RiichiMock(redDoraCount = 3)

        // 驗證繼承自基礎介面的屬性
        assertEquals(13, config.initialHandSize)

        // 驗證日麻擴充屬性
        assertEquals(3, config.redDoraCount, "Should support configurable red dora count")
    }
}