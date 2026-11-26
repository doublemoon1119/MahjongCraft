package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證牌列依牌牆實際可能佔到的位置讓位：牌牆少了牌只會讓出空間，擋路的牌被摸走後牌列回到原位。 */
class MahjongWallClearanceTest {
    /** 完整牌牆四面對稱，每個座位看出去的讓位範圍都相同。 */
    @Test
    fun `every seat sees the same full wall`() {
        val tileLayout = taiwanTileLayout()
        val clearance = MahjongTileTableLayout.wallClearance(FULL_WALL_SIDE_LAYOUT, tileLayout, WALL_STACKS, fullWallPlacements(WALL_STACKS))
        val band = MahjongTileTableLayout.handRowBand()
        val south = MahjongTileTableLayout.rowSpan(MahjongTileTableLayout.seatWallClearance(clearance, MahjongTableSide.SOUTH), band.left, band.right)

        MahjongTableSide.entries.forEach { side ->
            val span = MahjongTileTableLayout.rowSpan(MahjongTileTableLayout.seatWallClearance(clearance, side), band.left, band.right)
            assertEquals(south.left, span.left, EPSILON, "Left limit differs for $side")
            assertEquals(south.right, span.right, EPSILON, "Right limit differs for $side")
        }
    }

    /** 牌牆依任意順序少掉牌時，每個座位手牌、副露與桌緣那幾排可用的範圍只會變寬。 */
    @Test
    fun `rows only widen as the wall loses tiles`() {
        val tileLayout = taiwanTileLayout()
        val seatLayout = MahjongTableSeatLayout.forPlayerCount(PLAYER_COUNT)
        val remaining = fullWallPlacements(WALL_STACKS).shuffled(Random(SEED)).toMutableList()
        var previous = spans(tileLayout, seatLayout, remaining)
        while (remaining.isNotEmpty()) {
            repeat(minOf(TILES_PER_STEP, remaining.size)) { remaining.removeAt(remaining.lastIndex) }
            val current = spans(tileLayout, seatLayout, remaining)
            previous.zip(current).forEach { (before, after) ->
                assertTrue(after.left <= before.left + EPSILON && after.right >= before.right - EPSILON, "A row narrowed from $before to $after")
            }
            previous = current
        }
    }

    /**
     * 台麻牌牆往左錯開，擋到的是玩家右手邊。擋路的牌被摸走後，被擋的那幾排右邊讓出空間、左邊不變，手牌不會比之前更往左讓。
     */
    @Test
    fun `rows come back once the stacks in their way are drawn`() {
        val tileLayout = taiwanTileLayout()
        val full = fullWallPlacements(WALL_STACKS)
        val fullClearance = seatClearance(tileLayout, full)
        val blockedRows = rowBands().mapNotNull { (near, far) ->
            val blocking = full.filter { placement ->
                seatFootprints(tileLayout, listOf(placement)).single().let { it.x > 0.0 && it.z + it.halfZ > near && it.z - it.halfZ < far }
            }
            blocking.takeIf { it.isNotEmpty() }?.let { Triple(near, far, it) }
        }
        assertTrue(blockedRows.isNotEmpty(), "The full wall should reach a row on the right-hand side")
        blockedRows.forEach { (near, far, blocking) ->
            val drawnClearance = seatClearance(tileLayout, full - blocking.toSet())
            val fullSpan = MahjongTileTableLayout.rowSpan(fullClearance, near, far)
            val drawnSpan = MahjongTileTableLayout.rowSpan(drawnClearance, near, far)
            assertTrue(drawnSpan.right > fullSpan.right, "Drawing the blocking stacks should free the right side of the row at $near..$far")
            assertEquals(fullSpan.left, drawnSpan.left, EPSILON)
        }

        val drawnClearance = seatClearance(tileLayout, full - blockedRows.flatMap { it.third }.toSet())
        (0..MAX_SET_ASIDE_TILES).forEach { setAsideTileCount ->
            val shiftWithFullWall = handShift(tileLayout, fullClearance, setAsideTileCount)
            val shiftAfterDrawing = handShift(tileLayout, drawnClearance, setAsideTileCount)
            assertTrue(shiftAfterDrawing <= shiftWithFullWall + EPSILON, "The hand moved further left after the wall made room for $setAsideTileCount tiles")
        }
    }

    /** @return 手牌、牌牆與手牌之間、桌緣那三排的局部 Z 範圍。 */
    private fun rowBands(): List<Pair<Double, Double>> {
        val halfDepth = MahjongTileDimensions.TILE_HEIGHT / 2.0
        val band = MahjongTileTableLayout.handRowBand()
        return listOf(
            band.left to band.right,
            MahjongTileTableLayout.FRONT_MELD_ROW_Z - halfDepth to MahjongTileTableLayout.FRONT_MELD_ROW_Z + halfDepth,
            MahjongTileTableLayout.EDGE_ROW_Z - halfDepth to MahjongTileTableLayout.EDGE_ROW_Z + halfDepth,
        )
    }

    /**
     * 各座位手牌、副露與桌緣那幾排的可用範圍。
     *
     * @return 依座位與列排列的範圍。
     */
    private fun spans(tileLayout: TableTileLayoutProfile, seatLayout: MahjongTableSeatLayout, placements: List<TileWallPlacement>): List<RowSpan> {
        val clearance = MahjongTileTableLayout.wallClearance(FULL_WALL_SIDE_LAYOUT, tileLayout, WALL_STACKS, placements)
        return (0 until PLAYER_COUNT).flatMap { seatIndex ->
            val seat = clearance.forSeat(seatLayout, seatIndex)
            rowBands().map { (near, far) -> MahjongTileTableLayout.rowSpan(seat, near, far) }
        }
    }

    /** @return 南側座位看出去的讓位範圍。 */
    private fun seatClearance(tileLayout: TableTileLayoutProfile, placements: List<TileWallPlacement>): MahjongSeatWallClearance = MahjongTileTableLayout.seatWallClearance(
        clearance = MahjongTileTableLayout.wallClearance(FULL_WALL_SIDE_LAYOUT, tileLayout, WALL_STACKS, placements),
        side = MahjongTableSide.SOUTH,
    )

    /** @return 南側座位看出去，每張牌佔用的範圍。 */
    private fun seatFootprints(tileLayout: TableTileLayoutProfile, placements: List<TileWallPlacement>): List<SeatWallFootprint> = seatClearance(tileLayout, placements).footprints

    /** @return 一般手牌加上移出的牌時，手牌往左讓的距離。 */
    private fun handShift(tileLayout: TableTileLayoutProfile, clearance: MahjongSeatWallClearance, setAsideTileCount: Int): Double = MahjongPlayerAreaLayout.handShift(
        tileLayout = tileLayout,
        wallClearance = clearance,
        cornerWidth = 0.0,
        melds = emptyList(),
        setAsideTileCount = setAsideTileCount,
        standingTileCount = HAND_SIZE,
        playerCount = PLAYER_COUNT,
    )

    /** @return 內建台麻登記的桌面牌位配置。 */
    private fun taiwanTileLayout(): TableTileLayoutProfile = TableTileLayoutProfileRegistryImpl().apply {
        BundledMinecraftMahjongExtensions.all.forEach { it.registerTableTileLayoutProfiles(this) }
    }.profileFor(BuiltInRuleModuleIds.TAIWAN)

    private companion object {
        /** 台麻有花牌時每面的墩數。 */
        const val WALL_STACKS: Int = 18

        /** 玩家人數。 */
        const val PLAYER_COUNT: Int = 4

        /** 台麻立牌張數。 */
        const val HAND_SIZE: Int = 16

        /** 一副牌的花牌數，也就是一位玩家最多移出的牌數。 */
        const val MAX_SET_ASIDE_TILES: Int = 8

        /** 每一步從牌牆移走的牌數。 */
        const val TILES_PER_STEP: Int = 3

        /** 隨機種子。 */
        const val SEED: Int = 11

        /** 浮點比較容許誤差。 */
        const val EPSILON: Double = 1e-9
    }
}
