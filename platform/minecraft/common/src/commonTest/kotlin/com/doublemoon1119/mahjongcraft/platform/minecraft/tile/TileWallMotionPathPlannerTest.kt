package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證牌牆路徑分類、圓角、時長與桌面朝向等價性。 */
class TileWallMotionPathPlannerTest {
    /** 同一牆面上的水平移動只需要一段。 */
    @Test
    fun `same side movement uses one segment`() {
        val result = completedPath(position(0, 4, 0), position(0, 5, 0))

        assertEquals(1, result.segments.size)
        assertTrue(result.segments.single().durationTicks > 0)
    }

    /** 原地上下換層只建立垂直線段。 */
    @Test
    fun `layer movement uses one vertical segment`() {
        val result = completedPath(position(0, 4, 1), position(0, 4, 0))
        val segment = result.segments.single()

        assertEquals(segment.start.x, segment.end.x, TOLERANCE)
        assertEquals(segment.start.z, segment.end.z, TOLERANCE)
        assertTrue(segment.start.y > segment.end.y)
    }

    /** 相鄰牆面使用含圓角節點的多段路徑，且不以單一直線切過內側。 */
    @Test
    fun `adjacent sides use rounded outer path`() {
        val result = completedPath(position(0, 16, 0), position(1, 0, 0))

        assertTrue(result.segments.size >= 3)
        val centerX = CONTEXT.controllerX + 0.5
        val centerZ = CONTEXT.controllerZ + 0.5
        val directMidX = (result.segments.first().start.x + result.segments.last().end.x) / 2.0
        val directMidZ = (result.segments.first().start.z + result.segments.last().end.z) / 2.0
        val pathMiddle = result.segments[result.segments.size / 2].end
        assertTrue(
            hypot(pathMiddle.x - centerX, pathMiddle.z - centerZ) >
                hypot(directMidX - centerX, directMidZ - centerZ),
        )
    }

    /** 同一個 base position 的小數位移進入牆角時，也必須辨識為圓角路徑。 */
    @Test
    fun `fractional offset in last stack uses rounded corner path`() {
        val start = position(0, 16, 0)
        val end = start.copy(offset = TileWallPlacementOffset(alongWallStacks = 0.25))
        val result = completedPath(start, end)

        assertTrue(result.segments.size >= 3)
    }

    /** 跨越不相鄰牆面必須拒絕，不得退化成穿過桌面的直線。 */
    @Test
    fun `non adjacent sides are rejected`() {
        val decision = TileWallMotionPathPlanner.plan(CONTEXT, position(0, 8, 0), position(2, 8, 0))

        assertEquals(
            TileWallMotionPathDecision.Rejected(TileWallMotionPathRejection.NON_ADJACENT_SIDES),
            decision,
        )
    }

    /** 同時水平移動與換層時必須由 presenter 拆開，不由 planner 猜測先後。 */
    @Test
    fun `mixed horizontal and vertical movement is rejected`() {
        val decision = TileWallMotionPathPlanner.plan(CONTEXT, position(0, 4, 0), position(0, 5, 1))

        assertEquals(
            TileWallMotionPathDecision.Rejected(TileWallMotionPathRejection.MIXED_HORIZONTAL_AND_VERTICAL),
            decision,
        )
    }

    /** 桌子旋轉後路徑段數、時長與各段長度保持一致。 */
    @Test
    fun `table facing rotation preserves path geometry`() {
        val north = completedPath(position(0, 16, 0), position(1, 0, 0), MahjongTableFacing.NORTH)
        val east = completedPath(position(0, 16, 0), position(1, 0, 0), MahjongTableFacing.EAST)

        assertEquals(north.segments.map { it.durationTicks }, east.segments.map { it.durationTicks })
        north.segments.zip(east.segments).forEach { (northSegment, eastSegment) ->
            assertEquals(length(northSegment), length(eastSegment), TOLERANCE)
        }
    }

    /** 最短 yaw 角差不會繞遠路。 */
    @Test
    fun `shortest yaw delta crosses zero correctly`() {
        assertEquals(20.0f, shortestYawDeltaDegrees(350.0f, 10.0f))
        assertEquals(-20.0f, shortestYawDeltaDegrees(10.0f, 350.0f))
    }

    /** 取得指定起訖 placement 的成功路徑。 */
    private fun completedPath(
        start: TileWallPlacement,
        end: TileWallPlacement,
        facing: MahjongTableFacing = MahjongTableFacing.NORTH,
    ): TileWallMotionPathDecision.Completed = assertIs(
        TileWallMotionPathPlanner.plan(CONTEXT.copy(tableFacing = facing), start, end),
    )

    /** 建立測試用零 offset placement。 */
    private fun position(side: Int, stack: Int, layer: Int): TileWallPlacement = TileWallPlacement(TileWallPosition(side, stack, layer))

    /** 計算單一路徑段的三維長度。 */
    private fun length(segment: TileWallMotionSegment): Double {
        val dx = segment.end.x - segment.start.x
        val dy = segment.end.y - segment.start.y
        val dz = segment.end.z - segment.start.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** 測試共用桌面投影。 */
    private companion object {
        val CONTEXT = MahjongTileWallProjectionContext(0, 64, 0, MahjongTableFacing.NORTH, 0, 17)
        const val TOLERANCE = 1e-9
    }
}
