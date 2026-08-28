package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
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
     * 測試透過 copy() 產生新的 [TableState] 時，動態狀態的變更能正確反映在新實例上，
     * 且不影響原本的 [TableState]。
     */
    @Test
    fun `test riichi stick count in table state`() {
        val riichiDynamic = RiichiDynamicState(riichiStickCount = 5)

        val table = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(Wind.EAST),
                FakeMahjongPlayerFactory.create(Wind.SOUTH),
            ),
            config = RiichiRuleConfig(),
            dynamicRuleState = riichiDynamic,
        )

        val state = table.dynamicRuleState
        assertTrue(state is RiichiDynamicState, "The dynamic state should be an instance of RiichiDynamicState.")
        assertEquals(5, state.riichiStickCount)

        val updatedTable = table.copy(
            dynamicRuleState = riichiDynamic.copy(riichiStickCount = riichiDynamic.riichiStickCount + 1),
        )

        assertEquals(
            6,
            (updatedTable.dynamicRuleState as RiichiDynamicState).riichiStickCount,
            "The new TableState instance should reflect the updated riichi stick count.",
        )
        assertEquals(
            5,
            state.riichiStickCount,
            "The original TableState instance should remain unchanged.",
        )
    }
}
