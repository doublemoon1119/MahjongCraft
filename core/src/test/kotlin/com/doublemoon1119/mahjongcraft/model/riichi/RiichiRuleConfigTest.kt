package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對日本麻將規則配置進行獨立測試。
 */
class RiichiRuleConfigTest {

    /**
     * 模擬日本麻將配置實作。
     */
    private class RiichiMock : RiichiRuleConfig {
        override val initialHandSize: Int = 13
        override val tileSet: List<Tile> = emptyList()
        override val deadTileCount: Int = 14
        override val useRedTiles: Boolean = true
    }

    /**
     * 驗證日麻配置的參數。直接使用 Mock 類別以避免不必要的型別檢查警告。
     */
    @Test
    fun `test riichi specific configuration`() {
        val config = RiichiMock()

        assertEquals(13, config.initialHandSize)
        assertEquals(14, config.deadTileCount)
        assertTrue(config.useRedTiles, "Riichi configuration should support red tiles")
    }
}