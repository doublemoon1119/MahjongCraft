package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallTileMove
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 transition move 的拆段順序、phase barrier 與整墩同步能力。 */
class TileWallTransitionMotionPlannerTest {
    /** 初始開門只規劃與 assembly 不同的牌，並以單一同步 phase 表達。 */
    @Test
    fun `opening plan moves only changed placements in one phase`() {
        val stationaryId = Uuid.random()
        val movingId = Uuid.random()
        val stationaryPosition = TileWallPosition(0, 4, 0)
        val movingPosition = TileWallPosition(0, 5, 0)
        val destination = TileWallPlacement(
            movingPosition,
            TileWallPlacementOffset(alongWallStacks = 0.25),
        )

        val decision = TileWallTransitionMotionPlanner.planOpening(
            CONTEXT,
            mapOf(stationaryId to stationaryPosition, movingId to movingPosition),
            TileWallPhysicalLayout(
                mapOf(
                    stationaryId to TileWallPlacement(stationaryPosition),
                    movingId to destination,
                ),
            ),
        )

        val plan = assertIs<TileWallTransitionMotionDecision.Completed>(decision).plan
        assertEquals(1, plan.phases.size)
        assertEquals(listOf(movingId), plan.phases.single().motions.map { it.tileId })
        assertEquals(destinationWorld(destination), plan.phases.single().motions.single().segments.last().end)
    }

    /** 同時上升與水平移動時，必須先在來源處上升再沿上層滑入。 */
    @Test
    fun `rising mixed move goes vertical before horizontal`() {
        val source = placement(0, 5, 0)
        val destination = placement(0, 6, 1, 0.25)
        val motion = completedPlan(listOf(phase(move(source, destination)))).phases.single().motions.single()

        assertEquals(2, motion.segments.size)
        assertEquals(motion.segments.first().start.x, motion.segments.first().end.x, TOLERANCE)
        assertEquals(motion.segments.first().start.z, motion.segments.first().end.z, TOLERANCE)
        assertTrue(motion.segments.first().end.y > motion.segments.first().start.y)
        assertEquals(destinationWorld(destination), motion.segments.last().end)
    }

    /** 同時下降與水平移動時，必須先在原高度滑行再於目的位置下降。 */
    @Test
    fun `descending mixed move goes horizontal before vertical`() {
        val source = placement(0, 5, 1)
        val destination = placement(0, 6, 0, 0.25)
        val motion = completedPlan(listOf(phase(move(source, destination)))).phases.single().motions.single()

        assertEquals(2, motion.segments.size)
        assertEquals(motion.segments.first().start.y, motion.segments.first().end.y, TOLERANCE)
        assertEquals(motion.segments.last().start.x, motion.segments.last().end.x, TOLERANCE)
        assertEquals(motion.segments.last().start.z, motion.segments.last().end.z, TOLERANCE)
        assertTrue(motion.segments.last().end.y < motion.segments.last().start.y)
    }

    /** 下一個 phase 的相對開始時間應等於前一 phase 最長 motion 的完成時間。 */
    @Test
    fun `phase duration uses longest synchronized motion`() {
        val short = move(placement(0, 3, 0), placement(0, 4, 0))
        val long = move(placement(0, 6, 0), placement(0, 9, 0))
        val second = move(placement(0, 10, 1), placement(0, 10, 0))
        val plan = completedPlan(listOf(phase(short, long), phase(second)))

        assertEquals(plan.phases.first().motions.maxOf { it.durationTicks }, plan.phases.first().durationTicks)
        assertEquals(plan.phases.sumOf { it.durationTicks }, plan.durationTicks)
    }

    /** 同一 phase 的上下層整墩具有相同路徑時長與固定垂直距離。 */
    @Test
    fun `whole stack motions keep matching timing`() {
        val bottom = move(placement(0, 16, 0), placement(1, 0, 0))
        val top = move(placement(0, 16, 1), placement(1, 0, 1))
        val motions = completedPlan(listOf(phase(bottom, top))).phases.single().motions

        assertEquals(motions[0].segments.map { it.durationTicks }, motions[1].segments.map { it.durationTicks })
        motions[0].segments.zip(motions[1].segments).forEach { (bottomSegment, topSegment) ->
            assertEquals(
                topSegment.start.y - bottomSegment.start.y,
                topSegment.end.y - bottomSegment.end.y,
                TOLERANCE,
            )
        }
    }

    /** 建立單張測試 move。 */
    private fun move(source: TileWallPlacement, destination: TileWallPlacement): PhysicalWallTileMove = PhysicalWallTileMove(Uuid.random(), source, destination)

    /** 建立一個同步測試 phase。 */
    private fun phase(vararg moves: PhysicalWallTileMove): PhysicalWallLayoutTransitionPhase = PhysicalWallLayoutTransitionPhase(moves.toList())

    /** 取得成功規劃的 transition。 */
    private fun completedPlan(phases: List<PhysicalWallLayoutTransitionPhase>): TileWallTransitionMotionPlan = assertIs<TileWallTransitionMotionDecision.Completed>(
        TileWallTransitionMotionPlanner.plan(CONTEXT, phases),
    ).plan

    /** 建立測試用 placement。 */
    private fun placement(side: Int, stack: Int, layer: Int, along: Double = 0.0): TileWallPlacement = TileWallPlacement(
        position = TileWallPosition(side, stack, layer),
        offset = TileWallPlacementOffset(alongWallStacks = along),
    )

    /** 投影指定 placement。 */
    private fun destinationWorld(placement: TileWallPlacement): MahjongTileWallPlacement = CONTEXT.project(placement)

    /** 測試共用常數。 */
    private companion object {
        val CONTEXT = MahjongTileWallProjectionContext(0, 64, 0, MahjongTableFacing.NORTH, 0, 17)
        const val TOLERANCE = 1e-9
    }
}
