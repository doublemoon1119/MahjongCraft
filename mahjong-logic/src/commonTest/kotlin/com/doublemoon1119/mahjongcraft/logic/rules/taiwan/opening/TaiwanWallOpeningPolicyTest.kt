package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.opening

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證四人台灣麻將的三骰牌牆開門公式。 */
class TaiwanWallOpeningPolicyTest {
    /** 驗證三骰全部可能總和會依莊家起算的逆時針方向環繞四面牌牆。 */
    @Test
    fun `dice totals select wall side counter clockwise from dealer`() {
        val expectedSideOffsets = mapOf(
            3 to 2,
            4 to 3,
            5 to 0,
            6 to 1,
            7 to 2,
            8 to 3,
            9 to 0,
            10 to 1,
            11 to 2,
            12 to 3,
            13 to 0,
            14 to 1,
            15 to 2,
            16 to 3,
            17 to 0,
            18 to 1,
        )

        expectedSideOffsets.forEach { (total, expectedSideOffset) ->
            val opening = TaiwanWallOpeningPolicy.resolve(diceRollWithTotal(total))

            assertEquals(expectedSideOffset, opening.wallSideOffsetFromDealer, "Unexpected wall side for total $total")
            assertEquals(total, opening.stacksFromRight, "Unexpected stack count for total $total")
        }
    }

    /** 驗證規則範例中的十一點會落在莊家逆時針第二面（對家）並從右側數十一墩。 */
    @Test
    fun `dice total eleven resolves the documented opening`() {
        assertEquals(
            WallOpening(wallSideOffsetFromDealer = 2, stacksFromRight = 11),
            TaiwanWallOpeningPolicy.resolve(DiceRollResult.of(listOf(4, 4, 3))),
        )
    }

    /** 驗證四人台灣麻將不接受非三骰結果。 */
    @Test
    fun `taiwan opening rejects a non triple dice result`() {
        assertFailsWith<IllegalArgumentException> {
            TaiwanWallOpeningPolicy.resolve(DiceRollResult.of(listOf(6, 6)))
        }
        assertFailsWith<IllegalArgumentException> {
            TaiwanWallOpeningPolicy.resolve(DiceRollResult.of(listOf(1, 2, 3, 4)))
        }
    }

    /** 建立具有指定總和的合法三骰結果。 */
    private fun diceRollWithTotal(total: Int): DiceRollResult {
        val first = (total - 12).coerceAtLeast(1)
        val second = (total - first - 6).coerceAtLeast(1)
        val third = total - first - second
        return DiceRollResult.of(listOf(first, second, third))
    }
}
