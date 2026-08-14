package com.doublemoon1119.mahjongcraft.logic.rules.riichi.opening

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證四人日本麻將的雙骰牌牆開門公式。 */
class RiichiWallOpeningPolicyTest {
    /** 驗證雙骰全部可能總和會依莊家起算的逆時針方向環繞四面牌牆。 */
    @Test
    fun `dice totals select wall side counter clockwise from dealer`() {
        val expectedSideOffsets = mapOf(
            2 to 1,
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
        )

        expectedSideOffsets.forEach { (total, expectedSideOffset) ->
            val opening = RiichiWallOpeningPolicy.resolve(diceRollWithTotal(total))

            assertEquals(expectedSideOffset, opening.wallSideOffsetFromDealer, "Unexpected wall side for total $total")
            assertEquals(total, opening.stacksFromRight, "Unexpected stack count for total $total")
        }
    }

    /** 驗證規則範例中的八點會落在莊家逆時針第三面並從右側數八墩。 */
    @Test
    fun `dice total eight resolves the documented opening`() {
        assertEquals(
            WallOpening(wallSideOffsetFromDealer = 3, stacksFromRight = 8),
            RiichiWallOpeningPolicy.resolve(DiceRollResult.of(listOf(3, 5))),
        )
    }

    /** 驗證四人日本麻將不接受非雙骰結果。 */
    @Test
    fun `riichi opening rejects a non double dice result`() {
        assertFailsWith<IllegalArgumentException> {
            RiichiWallOpeningPolicy.resolve(DiceRollResult.of(listOf(6)))
        }
        assertFailsWith<IllegalArgumentException> {
            RiichiWallOpeningPolicy.resolve(DiceRollResult.of(listOf(1, 2, 3)))
        }
    }

    /** 建立具有指定總和的合法雙骰結果。 */
    private fun diceRollWithTotal(total: Int): DiceRollResult {
        val first = (total - 6).coerceAtLeast(1)
        return DiceRollResult.of(listOf(first, total - first))
    }
}
