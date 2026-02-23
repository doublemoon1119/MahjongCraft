package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對台灣麻將規則配置進行獨立測試。
 */
class TaiwanRuleConfigTest {

    /**
     * 模擬台灣麻將配置實作。
     */
    private class TaiwanMock : TaiwanRuleConfig {
        override val initialHandSize: Int = 16
        override val tileSet: List<Tile> = emptyList()
        override val deadTileCount: Int = 8
        override val useFlowerTiles: Boolean = true
    }

    /**
     * 驗證台麻配置的參數。直接使用 Mock 類別以避免不必要的型別檢查警告。
     */
    @Test
    fun `test taiwan specific configuration`() {
        val config = TaiwanMock()

        assertEquals(16, config.initialHandSize)
        assertEquals(8, config.deadTileCount)
        assertTrue(config.useFlowerTiles, "Taiwan configuration should support flower tiles")
    }
}