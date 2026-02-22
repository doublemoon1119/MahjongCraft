package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.TableState
import com.doublemoon1119.mahjongcraft.model.TileWall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對日本麻將特有的桌況狀態進行測試。
 * 位於 riichi 子套件下，允許依賴日麻實作。
 */
class RiichiTableStateTest {

    @Test
    fun `test riichi stick count in table state`() {
        val riichiExtra = RiichiExtraState(riichiStickCount = 5)
        val table = TableState(
            players = emptyList(),
            tileWall = TileWall(mutableListOf()),
            extraState = riichiExtra
        )

        // 驗證 extraState 的型別轉換與屬性存取
        val state = table.extraState
        assertTrue(state is RiichiExtraState, "extraState should be instance of RiichiExtraState")
        assertEquals(5, state.riichiStickCount)

        // 模擬立直棒更新
        state.riichiStickCount += 1
        assertEquals(6, table.extraState.riichiStickCount)
    }
}