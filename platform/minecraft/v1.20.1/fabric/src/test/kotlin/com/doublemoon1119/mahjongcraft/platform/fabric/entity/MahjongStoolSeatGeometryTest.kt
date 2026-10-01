package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolDesign
import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證 [MahjongStoolSeatGeometry] 的坐姿高度與下座順序。 */
class MahjongStoolSeatGeometryTest {
    /** 座位頂端與凳面齊平並嵌在凳子裡；乘客的屁股底面貼齊凳面。 */
    @Test
    fun `the seat sits inside the stool and the passenger bottom meets the seat top`() {
        MahjongStoolDesign.entries.forEach { design ->
            val seatY = MahjongStoolSeatGeometry.seatY(design.seatHeight)
            val feetY = MahjongStoolSeatGeometry.passengerFeetY(seatY)

            assertEquals(design.seatHeight, seatY + MahjongStoolSeatGeometry.SEAT_SIZE, absoluteTolerance = 1e-9)
            assertTrue(seatY >= 0.0, "$design seat must stay inside the stool block")
            assertEquals(design.seatHeight, feetY + MahjongStoolSeatGeometry.SEATED_BOTTOM_HEIGHT, absoluteTolerance = 1e-9)
        }
    }

    /** 坐在凳子上的玩家，眼睛約在桌面上方 0.6 格；桌面固定在凳子所在高度往上 1 格。 */
    @Test
    fun `a seated player looks down on the table from a comfortable height`() {
        MahjongStoolDesign.entries.forEach { design ->
            val feetY = MahjongStoolSeatGeometry.passengerFeetY(MahjongStoolSeatGeometry.seatY(design.seatHeight))

            assertEquals(0.5965625, feetY + PLAYER_EYE_HEIGHT - TABLE_TOP_HEIGHT, absoluteTolerance = 1e-9)
        }
    }

    /** 下座時先往面前，再往右、往左，最後往後。 */
    @Test
    fun `dismounting tries the front first then the sides and the back`() {
        assertEquals(
            listOf(Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH),
            MahjongStoolSeatGeometry.dismountDirections(Direction.NORTH),
        )
        assertEquals(
            listOf(Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.WEST),
            MahjongStoolSeatGeometry.dismountDirections(Direction.EAST),
        )
    }

    private companion object {
        /** 原版玩家站姿的眼睛高度。 */
        const val PLAYER_EYE_HEIGHT: Double = 1.62

        /** 麻將桌桌面相對凳子所在方塊底部的高度。 */
        const val TABLE_TOP_HEIGHT: Double = 1.0
    }
}
