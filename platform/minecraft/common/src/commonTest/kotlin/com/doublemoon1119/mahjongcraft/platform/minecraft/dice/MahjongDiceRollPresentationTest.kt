package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** [MahjongDiceRollPresentation] 的權威輸入驗證測試。 */
class MahjongDiceRollPresentationTest {
    /** 兩顆與三顆骰子應保留各自點數及 animation seed。 */
    @Test
    fun `presentation accepts two or three authoritative dice`() {
        listOf(2, 3).forEach { diceCount ->
            val dice = List(diceCount) { index -> MahjongDicePresentation(index + 1, 100L + index) }
            val presentation = presentation(dice = dice)

            assertEquals(dice, presentation.dice)
        }
    }

    /** 一顆或四顆骰子尚未具有安全桌面 layout，應拒絕呈現。 */
    @Test
    fun `presentation rejects unsupported dice counts`() {
        assertFailsWith<IllegalArgumentException> {
            presentation(dice = listOf(MahjongDicePresentation(1, 1L)))
        }
        assertFailsWith<IllegalArgumentException> {
            presentation(dice = List(4) { MahjongDicePresentation(1, it.toLong()) })
        }
    }

    /** 無效點數與負投擲序號應在版本 adapter 前被拒絕。 */
    @Test
    fun `presentation rejects invalid authoritative values`() {
        assertFailsWith<IllegalArgumentException> { MahjongDicePresentation(0, 1L) }
        assertFailsWith<IllegalArgumentException> { MahjongDicePresentation(7, 1L) }
        assertFailsWith<IllegalArgumentException> { presentation(rollSequence = -1) }
    }

    /** 建立具有固定桌子資料的測試 presentation。 */
    private fun presentation(
        rollSequence: Long = 0,
        dice: List<MahjongDicePresentation> = listOf(
            MahjongDicePresentation(1, 1L),
            MahjongDicePresentation(2, 2L),
        ),
    ): MahjongDiceRollPresentation = MahjongDiceRollPresentation(
        tableId = TABLE_ID,
        tableLocation = TableLocation("minecraft:overworld", 0, 64, 0),
        tableFacing = MahjongTableFacing.NORTH,
        throwSide = MahjongTableSide.SOUTH,
        rollSequence = rollSequence,
        dice = dice,
    )

    /** 測試使用的固定桌子 UUID。 */
    private companion object {
        val TABLE_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")
    }
}
