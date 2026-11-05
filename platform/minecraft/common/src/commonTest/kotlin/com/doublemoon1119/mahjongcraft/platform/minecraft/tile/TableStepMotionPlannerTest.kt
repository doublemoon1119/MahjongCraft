package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證比較前後兩份擺放後選出的動作與步驟時長。 */
class TableStepMotionPlannerTest {
    /** 這組測試的玩家。 */
    private val player = Uuid.random()

    /** 立牌那一排。 */
    private val hand = TileRowKey(TileRegion.HAND, player, null)

    /** 從牌牆摸起、打到牌河、立牌與摸牌位之間各自選出對應的動作。 */
    @Test
    fun `region changes choose draw, flight and arc`() {
        val wallTile = Uuid.random()
        val discarded = Uuid.random()
        val drawn = Uuid.random()
        val previous = arrangement(
            arranged(wallTile, TileRowKey(TileRegion.WALL, null, null), 0, x = 0.0),
            arranged(discarded, hand, 0, x = 1.0),
            arranged(drawn, TileRowKey(TileRegion.DRAWN, player, null), 0, x = 2.0),
        )
        val next = arrangement(
            arranged(wallTile, TileRowKey(TileRegion.DRAWN, player, null), 0, x = 3.0),
            arranged(discarded, TileRowKey(TileRegion.DISCARD, player, null), 0, x = 4.0),
            arranged(drawn, hand, 0, x = 1.0),
        )

        val kinds = TableStepMotionPlanner.plan(previous, next).associate { it.tileId to it.kind }

        assertEquals(TileMotionKind.DRAW, kinds[wallTile])
        assertEquals(TileMotionKind.FLIGHT, kinds[discarded])
        assertEquals(TileMotionKind.ARC, kinds[drawn])
    }

    /** 同一排補位時維持前後順序的牌平移；越過其他牌的那一張走弧線，沒有移動的牌不規劃動作。 */
    @Test
    fun `row reordering slides the order-preserving tiles and arcs the rest`() {
        val (a, b, c, d) = List(4) { Uuid.random() }
        val previous = arrangement(
            arranged(a, hand, 0, x = 0.0),
            arranged(b, hand, 1, x = 1.0),
            arranged(c, hand, 2, x = 2.0),
            arranged(d, hand, 3, x = 3.0),
        )
        // d 移到最前面，其餘三張往後讓位。
        val next = arrangement(
            arranged(d, hand, 0, x = 0.0),
            arranged(a, hand, 1, x = 1.0),
            arranged(b, hand, 2, x = 2.0),
            arranged(c, hand, 3, x = 3.0),
        )

        val kinds = TableStepMotionPlanner.plan(previous, next).associate { it.tileId to it.kind }

        assertEquals(TileMotionKind.ARC, kinds[d])
        listOf(a, b, c).forEach { assertEquals(TileMotionKind.SLIDE, kinds[it]) }
    }

    /** 位置沒變只改姿態時翻面；位置與姿態都沒變時沒有動作。 */
    @Test
    fun `pose changes turn in place and unchanged tiles are skipped`() {
        val turned = Uuid.random()
        val still = Uuid.random()
        val previous = arrangement(arranged(turned, hand, 0, x = 0.0), arranged(still, hand, 1, x = 1.0))
        val next = arrangement(arranged(turned, hand, 0, x = 0.0, pose = TilePose.FACE_UP), arranged(still, hand, 1, x = 1.0))

        val motions = TableStepMotionPlanner.plan(previous, next)

        assertEquals(listOf(turned to TileMotionKind.TURN), motions.map { it.tileId to it.kind })
    }

    /** 沒有先前擺放時直接擺放；排除的牌不規劃動作。 */
    @Test
    fun `missing previous arrangement places tiles and excluded tiles are skipped`() {
        val placed = Uuid.random()
        val excluded = Uuid.random()
        val next = arrangement(arranged(placed, hand, 0, x = 0.0), arranged(excluded, hand, 1, x = 1.0))

        val motions = TableStepMotionPlanner.plan(null, next, excludedTileIds = setOf(excluded))

        assertEquals(listOf(placed to TileMotionKind.PLACE), motions.map { it.tileId to it.kind })
    }

    /** 先離開、再讓位、最後摸進來，三段依序：每一段等前一段最長的動作完成；沒有動作的段落不佔時間。 */
    @Test
    fun `phases play in order and the step lasts the sum of their longest motions`() {
        val motions = listOf(TileMotionKind.FLIGHT, TileMotionKind.SLIDE, TileMotionKind.ARC, TileMotionKind.DRAW).map(::motion)
        val flight = TableStepMotionPlanner.durationTicks(TileMotionKind.FLIGHT)
        val makeRoom = maxOf(TableStepMotionPlanner.durationTicks(TileMotionKind.SLIDE), TableStepMotionPlanner.durationTicks(TileMotionKind.ARC))

        val starts = TableStepMotionPlanner.phaseStartTicks(motions)

        assertEquals(mapOf(TileMotionPhase.DEPART to 0, TileMotionPhase.MAKE_ROOM to flight, TileMotionPhase.ARRIVE to flight + makeRoom), starts)
        assertEquals(flight + makeRoom + TableStepMotionPlanner.durationTicks(TileMotionKind.DRAW), TableStepMotionPlanner.stepDurationTicks(motions))
        assertEquals(0, TableStepMotionPlanner.phaseStartTicks(listOf(motion(TileMotionKind.DRAW))).getValue(TileMotionPhase.ARRIVE))
        assertEquals(0, TableStepMotionPlanner.stepDurationTicks(emptyList()))
    }

    /** 立牌中的一個動作。 */
    private fun motion(kind: TileMotionKind) = PlannedTileMotion(Uuid.random(), kind, hand, hand, placement(0.0), placement(1.0), TilePose.UPRIGHT, TilePose.UPRIGHT)

    /** 最長遞增子序列回傳原位置，相同長度時取任一組都符合遞增。 */
    @Test
    fun `longest increasing subsequence keeps the most tiles in order`() {
        val values = listOf(3, 0, 1, 2)

        val indices = TableStepMotionPlanner.longestIncreasingSubsequence(values)

        assertEquals(listOf(1, 2, 3), indices)
        assertTrue(TableStepMotionPlanner.longestIncreasingSubsequence(emptyList()).isEmpty())
    }

    /** 以牌識別碼索引擺放。 */
    private fun arrangement(vararg tiles: ArrangedTile): Map<Uuid, ArrangedTile> = tiles.associateBy { it.tileId }

    /** 建立一張擺放在 [x] 的牌。 */
    private fun arranged(tileId: Uuid, row: TileRowKey, order: Int, x: Double, pose: TilePose = TilePose.UPRIGHT) = ArrangedTile(tileId, row, order, placement(x), pose)

    /** 只有 X 座標不同的位置。 */
    private fun placement(x: Double) = MahjongTileWallPlacement(x, 0.0, 0.0, 0f)
}
