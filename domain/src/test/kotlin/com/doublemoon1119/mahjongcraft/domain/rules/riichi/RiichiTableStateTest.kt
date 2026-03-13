package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.domain.table.TableState
import com.doublemoon1119.mahjongcraft.domain.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.fakes.FakeRiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對日本麻將特有的動態桌況狀態進行單元測試。
 *
 * 驗證 [TableState] 在持有日麻專屬動態狀態時的型別安全性與屬性變動。
 */
class RiichiTableStateTest {

    /**
     * 驗證日麻特有的立直棒（供託）計數功能。
     *
     * 測試動態狀態在 [TableState] 中的屬性變動是否能正確透過引用反映。
     */
    @Test
    fun `test riichi stick count in table state`() {
        val riichiDynamic = RiichiDynamicState(riichiStickCount = 5)

        val table = TableState(
            players = emptyList(),
            tileWall = TileWall(mutableListOf()),
            config = FakeRiichiRuleConfig(),
            dynamicRuleState = riichiDynamic
        )

        val state = table.dynamicRuleState
        assertTrue(state is RiichiDynamicState, "The dynamic state should be an instance of RiichiDynamicState.")
        assertEquals(5, state.riichiStickCount)

        riichiDynamic.riichiStickCount += 1

        assertEquals(
            6,
            table.dynamicRuleState.riichiStickCount,
            "Changes to the dynamic state object should be reflected in TableState."
        )
    }
}