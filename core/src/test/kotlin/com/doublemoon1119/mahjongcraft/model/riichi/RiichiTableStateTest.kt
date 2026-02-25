package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.config.GameLength
import com.doublemoon1119.mahjongcraft.model.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.model.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.model.table.TableState
import com.doublemoon1119.mahjongcraft.model.table.TileWall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對日本麻將特有的動態桌況狀態進行測試。
 * 位於 riichi 子套件下，允許依賴日麻實作與配置。
 */
class RiichiTableStateTest {

    /**
     * 模擬日麻專用的最簡配置實作。
     */
    private class RiichiMockConfig : RiichiRuleConfig {
        override val redDoraCount: Int = 3
        override val allowOpenTanyao: Boolean = true
        override val useLocalYaku: Boolean = false
        override val initialHandSize: Int = 13
        override val tileSet: List<Tile> = emptyList()
        override val deadTileCount: Int = 14
        override val minimumWinConstraint: Int = 1
        override val scoreConfig = RiichiScoreConfig()
        override val gameLength = object : GameLength {
            override val totalRounds: Int = 8
            override val name: String = "HALF_CHAN"
        }
    }

    /**
     * 驗證日麻特有的立直棒（供託）計數功能。
     * 測試動態狀態在 [TableState] 中的型別安全性與屬性變動。
     */
    @Test
    fun `test riichi stick count in table state`() {
        // 初始化日麻特有的動態狀態
        val riichiDynamic = RiichiDynamicState(riichiStickCount = 5)

        // 建立桌況，注入日麻專用配置與動態狀態
        val table = TableState(
            players = emptyList(),
            tileWall = TileWall(mutableListOf()),
            config = RiichiMockConfig(),
            dynamicRuleState = riichiDynamic
        )

        // 驗證 dynamicRuleState 的型別轉換與初始屬性存取
        val state = table.dynamicRuleState
        assertTrue(state is RiichiDynamicState, "dynamicRuleState should be instance of RiichiDynamicState")
        assertEquals(5, state.riichiStickCount, "Initial riichi stick count should be 5")

        // 模擬立直棒更新（如玩家立直時供託增加），並驗證狀態同步
        state.riichiStickCount += 1

        // 重新從 table 獲取狀態並驗證
        val updatedState = table.dynamicRuleState as RiichiDynamicState
        assertEquals(6, updatedState.riichiStickCount, "Riichi stick count should be updated to 6 via reference")
    }
}