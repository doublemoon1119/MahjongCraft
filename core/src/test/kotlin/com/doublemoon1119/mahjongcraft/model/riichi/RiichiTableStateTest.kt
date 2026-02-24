package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.table.TableState
import com.doublemoon1119.mahjongcraft.model.table.TileWall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對日本麻將特有的動態桌況狀態進行測試。
 * 位於 riichi 子套件下，允許依賴日麻實作。
 */
class RiichiTableStateTest {

    /**
     * 驗證日麻特有的立直棒（供託）計數功能。
     */
    @Test
    fun `test riichi stick count in table state`() {
        val riichiDynamic = RiichiDynamicState(riichiStickCount = 5)
        val table = TableState(
            players = emptyList(),
            tileWall = TileWall(mutableListOf()),
            dynamicRuleState = riichiDynamic
        )

        // 驗證 dynamicRuleState 的型別轉換與屬性存取
        val state = table.dynamicRuleState
        assertTrue(state is RiichiDynamicState, "dynamicRuleState should be instance of RiichiDynamicState")
        assertEquals(5, state.riichiStickCount)

        // 模擬立直棒更新，並驗證狀態同步
        state.riichiStickCount += 1
        val updatedState = table.dynamicRuleState as RiichiDynamicState
        assertEquals(6, updatedState.riichiStickCount, "Riichi stick count should be updated to 6")
    }
}