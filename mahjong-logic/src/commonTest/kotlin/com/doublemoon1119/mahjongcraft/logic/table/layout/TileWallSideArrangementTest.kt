package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout.RiichiWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiWallLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證規則提供的牌牆側面配置契約。 */
class TileWallSideArrangementTest {
    /** 驗證一到四面牌牆都能保留指定的標準側面。 */
    @Test
    fun `arrangement supports one through four distinct sides`() {
        (1..4).forEach { sideCount ->
            val arrangement = TileWallSideArrangement(
                reference = TileWallSideReference.TABLE,
                quarterTurns = (0 until sideCount).toList(),
            )

            assertEquals(sideCount, arrangement.sideCount)
            assertEquals((0 until sideCount).toList(), (0 until sideCount).map(arrangement::quarterTurnsOf))
        }
    }

    /** 驗證側面旋轉量只能落在四個標準方向內。 */
    @Test
    fun `arrangement rejects quarter turns outside standard directions`() {
        listOf(-1, 4).forEach { invalidQuarterTurns ->
            assertFailsWith<IllegalArgumentException> {
                TileWallSideArrangement(TileWallSideReference.TABLE, listOf(invalidQuarterTurns))
            }
        }
    }

    /** 驗證同一個標準側面不能被兩面牌牆重複使用。 */
    @Test
    fun `arrangement rejects duplicate sides`() {
        assertFailsWith<IllegalArgumentException> {
            TileWallSideArrangement(TileWallSideReference.TABLE, listOf(0, 0))
        }
    }

    /** 驗證建立配置後，呼叫端修改原始清單不會改變配置內容。 */
    @Test
    fun `arrangement copies the input list`() {
        val quarterTurns = mutableListOf(0, 2)
        val arrangement = TileWallSideArrangement(TileWallSideReference.TABLE, quarterTurns)

        quarterTurns[0] = 3

        assertEquals(0, arrangement.quarterTurnsOf(0))
        assertEquals(2, arrangement.quarterTurnsOf(1))
    }

    /** 驗證三人日麻依莊家 index 旋轉三面牌牆，維持原本的開門方向。 */
    @Test
    fun `three player layout maps sides relative to dealer`() {
        val wallLayout = ThreePlayerRiichiWallLayout(ThreePlayerRiichiRuleConfig())

        (0 until ThreePlayerRiichiWallLayout.SIDE_COUNT).forEach { dealerSeatIndex ->
            val arrangement = wallLayout.sideArrangement(dealerSeatIndex)

            assertEquals(
                List(ThreePlayerRiichiWallLayout.SIDE_COUNT) { side ->
                    (dealerSeatIndex - side).mod(ThreePlayerRiichiWallLayout.SIDE_COUNT)
                },
                (0 until arrangement.sideCount).map(arrangement::quarterTurnsOf),
            )
        }
    }

    /** 驗證四人日麻維持固定的莊家基準牌牆面順序。 */
    @Test
    fun `four player layout keeps the standard dealer arrangement`() {
        val arrangement = RiichiWallLayout(RiichiRuleConfig()).sideArrangement(dealerSeatIndex = 0)

        assertEquals(listOf(0, 3, 2, 1), (0 until arrangement.sideCount).map(arrangement::quarterTurnsOf))
    }
}
