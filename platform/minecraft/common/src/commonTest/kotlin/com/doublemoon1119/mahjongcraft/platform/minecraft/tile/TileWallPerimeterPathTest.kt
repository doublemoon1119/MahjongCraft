package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證相對牌牆的桌面外緣繞行路徑。 */
class TileWallPerimeterPathTest {
    /** 南北兩端會經過桌面東側或西側的兩個外側角點。 */
    @Test
    fun `north south endpoints use an outer perimeter path`() {
        val start = placement(x = 0.5, z = -2.0, yaw = 0f)
        val end = placement(x = 0.5, z = 2.0, yaw = 180f)

        val path = TileWallPerimeterPath.oppositeSidePath(start, end, 0.5, 0.5)

        assertEquals(start, path.first())
        assertEquals(end, path.last())
        assertEquals(4, path.size)
        assertTrue(path.drop(1).dropLast(1).all { it.x == 3.0 || it.x == -2.0 })
    }

    /** 東西兩端會經過桌面北側或南側的兩個外側角點。 */
    @Test
    fun `east west endpoints use an outer perimeter path`() {
        val start = placement(x = -2.0, z = 0.5, yaw = 90f)
        val end = placement(x = 2.0, z = 0.5, yaw = 270f)

        val path = TileWallPerimeterPath.oppositeSidePath(start, end, 0.5, 0.5)

        assertEquals(start, path.first())
        assertEquals(end, path.last())
        assertEquals(4, path.size)
        assertTrue(path.drop(1).dropLast(1).all { it.z == 3.0 || it.z == -2.0 })
    }

    /** 路徑中的 yaw 由起點平滑走到終點，且兩端保持原始 placement。 */
    @Test
    fun `path interpolates yaw and preserves endpoints`() {
        val start = placement(x = 0.5, z = -3.0, yaw = 350f)
        val end = placement(x = 0.5, z = 3.0, yaw = 170f)

        val path = TileWallPerimeterPath.oppositeSidePath(start, end, 0.5, 0.5)

        assertEquals(350f, path.first().yaw)
        assertEquals(170f, path.last().yaw)
        assertTrue(path[1].yaw != start.yaw && path[1].yaw != end.yaw)
    }

    /** 非 180 度朝向差不應被當成相對牆面路徑處理。 */
    @Test
    fun `non opposite yaw is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TileWallPerimeterPath.oppositeSidePath(
                start = placement(x = 0.5, z = -2.0, yaw = 0f),
                end = placement(x = 0.5, z = 2.0, yaw = 90f),
                centerX = 0.5,
                centerZ = 0.5,
            )
        }
    }

    /** 兩面牌牆位於相對桌邊時，planner 會選擇外緣路徑而不是穿越桌面。 */
    @Test
    fun `two opposite logical walls use perimeter planner`() {
        val context = MahjongTileWallProjectionContext(
            controllerX = 0,
            controllerY = 64,
            controllerZ = 0,
            tableFacing = MahjongTableFacing.NORTH,
            sideLayout = MahjongTileWallSideLayout.resolve(
                TileWallSideArrangement(TileWallSideReference.TABLE, listOf(0, 2)),
                dealerSide = MahjongTableSide.SOUTH,
            ),
            stacksPerSide = 17,
        )
        val decision = TileWallMotionPathPlanner.plan(
            context,
            TileWallPlacement(TileWallPosition(side = 0, stack = 8, layer = 0)),
            TileWallPlacement(TileWallPosition(side = 1, stack = 8, layer = 0)),
        )

        val completed = assertIs<TileWallMotionPathDecision.Completed>(decision)
        assertTrue(completed.segments.size >= 3)
    }

    private fun placement(x: Double, z: Double, yaw: Float): MahjongTileWallPlacement = MahjongTileWallPlacement(x = x, y = 64.0, z = z, yaw = yaw)
}
