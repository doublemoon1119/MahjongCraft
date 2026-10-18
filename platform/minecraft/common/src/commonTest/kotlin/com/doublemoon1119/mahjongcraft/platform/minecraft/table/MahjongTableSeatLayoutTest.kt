package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證不同玩家人數的標準座位布局。 */
class MahjongTableSeatLayoutTest {
    /** 驗證兩位玩家使用相對的南北兩側。 */
    @Test
    fun `two player layout places players opposite each other`() {
        val layout = MahjongTableSeatLayout.forPlayerCount(2)

        assertEquals(2, layout.playerCount)
        assertEquals(MahjongTableSide.SOUTH, layout.sideOf(0))
        assertEquals(MahjongTableSide.NORTH, layout.sideOf(1))
    }

    /** 驗證三位玩家使用南、東、北三側。 */
    @Test
    fun `three player layout leaves west side empty`() {
        val layout = MahjongTableSeatLayout.forPlayerCount(3)

        assertEquals(3, layout.playerCount)
        assertEquals(MahjongTableSide.SOUTH, layout.sideOf(0))
        assertEquals(MahjongTableSide.EAST, layout.sideOf(1))
        assertEquals(MahjongTableSide.NORTH, layout.sideOf(2))
    }

    /** 驗證四位玩家使用四個桌面側面。 */
    @Test
    fun `four player layout uses all four sides`() {
        val layout = MahjongTableSeatLayout.forPlayerCount(4)

        assertEquals(4, layout.playerCount)
        assertEquals(MahjongTableSide.SOUTH, layout.sideOf(0))
        assertEquals(MahjongTableSide.EAST, layout.sideOf(1))
        assertEquals(MahjongTableSide.NORTH, layout.sideOf(2))
        assertEquals(MahjongTableSide.WEST, layout.sideOf(3))
    }

    /** 驗證不支援的玩家人數會被拒絕。 */
    @Test
    fun `unsupported player count is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MahjongTableSeatLayout.forPlayerCount(1)
        }
        assertFailsWith<IllegalArgumentException> {
            MahjongTableSeatLayout.forPlayerCount(5)
        }
    }

    /** 驗證超出布局範圍的席位 index 會被拒絕。 */
    @Test
    fun `out of range seat index is rejected`() {
        val layout = MahjongTableSeatLayout.forPlayerCount(2)

        assertFailsWith<IndexOutOfBoundsException> { layout.sideOf(-1) }
        assertFailsWith<IndexOutOfBoundsException> { layout.sideOf(2) }
    }
}
