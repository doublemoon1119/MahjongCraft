package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證散放牌河的牌不重疊、不碰到牌牆與擲骰區，而且位置只由這家的第幾張與牌本身決定。 */
class ScatteredDiscardLayoutTest {
    /** 每家的格子互不重疊：四個方向的格子轉到同一個座標系後沒有重複。 */
    @Test
    fun `seat cells never share a cell with another seat`() {
        val cells = MahjongTileTableLayout.scatteredDiscardCells(PROFILE, WALL_STACKS)
        val all = (0 until 4).flatMap { turns -> cells.map { cell -> cell.rotated(turns) } }

        assertTrue(cells.size >= MIN_CELLS_PER_SEAT, "Expected at least $MIN_CELLS_PER_SEAT cells per seat, found ${cells.size}")
        assertEquals(all.size, all.map { it.rounded() }.toSet().size, "Cells of different seats overlap")
    }

    /** 每張牌偏轉並偏移後仍在自己的格子內、在牌牆內側、在擲骰區之外，偏轉角度不超過上限。 */
    @Test
    fun `scattered discards stay in their cell, inside the walls and outside the dice area`() {
        val cells = MahjongTileTableLayout.scatteredDiscardCells(PROFILE, WALL_STACKS)
        val half = MahjongTileTableLayout.SCATTER_CELL_SIZE / 2.0
        val wallInnerFace = WALL_STACKS / 2.0 * (MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING) -
            PROFILE.wallInset - MahjongTileDimensions.TILE_HEIGHT / 2.0
        (0 until 4).forEach { seat ->
            repeat(cells.size) { index ->
                val discard = MahjongTileTableLayout.scatteredDiscardLocal(seat, PROFILE, WALL_STACKS, index, Uuid.random())
                val cell = cells.minBy { hypot(it.x - discard.x, it.z - discard.z) }
                val corners = corners(discard)

                assertTrue(abs(discard.angleDegrees) <= MahjongTileTableLayout.SCATTER_MAX_ANGLE_DEGREES)
                assertTrue(corners.all { (x, z) -> abs(x - cell.x) <= half && abs(z - cell.z) <= half }, "Tile leaves its cell: $discard in $cell")
                assertTrue(corners.all { (x, z) -> abs(x) < wallInnerFace && abs(z) < wallInnerFace }, "Tile reaches a wall: $discard")
                assertTrue(hypot(cell.x, cell.z) >= MahjongTileTableLayout.SCATTER_DICE_CLEARANCE, "Cell inside the dice area: $cell")
            }
        }
    }

    /** 同一家的前幾張牌放在不同的格子，順序不照排列；格子用完後從第一格起疊在上一層。 */
    @Test
    fun `discards fill distinct cells and stack once the cells run out`() {
        val cells = MahjongTileTableLayout.scatteredDiscardCells(PROFILE, WALL_STACKS)
        val tileId = Uuid.random()
        val firstRound = (0 until cells.size).map { MahjongTileTableLayout.scatteredDiscardLocal(0, PROFILE, WALL_STACKS, it, tileId) }
        val secondLayer = MahjongTileTableLayout.scatteredDiscardLocal(0, PROFILE, WALL_STACKS, cells.size, tileId)

        assertEquals(cells.size, firstRound.map { Pair(it.x.rounded(), it.z.rounded()) }.toSet().size)
        assertTrue(firstRound.all { it.y == 0.0 })
        assertEquals(firstRound[0].x, secondLayer.x, EPSILON)
        assertEquals(firstRound[0].z, secondLayer.z, EPSILON)
        assertTrue(secondLayer.y > 0.0)
        assertNotEquals(firstRound.sortedBy { it.x }.map { it.x }, firstRound.map { it.x }, "Discards should not line up in order")
    }

    /** 被鳴走的牌只離開，同一家其他牌的位置不變。 */
    @Test
    fun `taking a discard leaves the other discards where they are`() {
        val tiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, it + 1)) }
        val pile = tiles.fold(FakeDiscardPile()) { acc, tile -> acc.discardTile(tile) }
        fun arrange(discardPile: FakeDiscardPile) = TableTileArrangement.arrange(
            TableTileArrangementInput(
                controllerX = 0,
                controllerY = 64,
                controllerZ = 0,
                tableFacing = MahjongTableFacing.NORTH,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(2),
                tableState = FakeTableStateFactory.create(
                    players = listOf(FakeMahjongPlayerFactory.create(Wind.EAST, discardPile = discardPile), FakeMahjongPlayerFactory.create(Wind.SOUTH)),
                ),
                tileLayout = PROFILE,
                cornerWidthBySeat = emptyMap(),
                handPoseStates = emptyList(),
                wallProjection = null,
                faceUpWallTileIds = emptySet(),
            ),
        )
        val before = arrange(pile)
        val after = arrange(pile.takeLast())

        assertEquals(before.getValue(tiles[0].id).placement, after.getValue(tiles[0].id).placement)
        assertEquals(before.getValue(tiles[1].id).placement, after.getValue(tiles[1].id).placement)
        assertTrue(tiles[2].id !in after)
    }

    /**
     * 牌偏轉後的四個角，局部座標。
     *
     * @param discard 牌的位置與偏轉。
     * @return 四個角的 (x, z)。
     */
    private fun corners(discard: ScatteredDiscard): List<Pair<Double, Double>> {
        val angle = discard.angleDegrees * PI / 180.0
        val halfWidth = MahjongTileDimensions.TILE_WIDTH / 2.0
        val halfHeight = MahjongTileDimensions.TILE_HEIGHT / 2.0
        return listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0).map { (sx, sz) ->
            val dx = sx * halfWidth
            val dz = sz * halfHeight
            Pair(discard.x + dx * cos(angle) - dz * sin(angle), discard.z + dx * sin(angle) + dz * cos(angle))
        }
    }

    /** @return 轉 [turns] 個九十度後的格子。 */
    private fun ScatterCell.rotated(turns: Int): ScatterCell = (0 until turns).fold(this) { cell, _ -> ScatterCell(-cell.z, cell.x) }

    /** @return 四捨五入到容許誤差的格心，用來比較是否相同。 */
    private fun ScatterCell.rounded(): Pair<Long, Long> = Pair(x.rounded(), z.rounded())

    /** @return 四捨五入到容許誤差的數值。 */
    private fun Double.rounded(): Long = round(this / EPSILON).toLong()

    private companion object {
        /** 台麻的桌面牌位配置。 */
        val PROFILE = TableTileLayoutProfile(
            wallInset = 0.105,
            wallStagger = TableWallStagger.LEFT,
            wallCornerOverhang = 0.18,
            meldArea = TableMeldArea.BETWEEN_WALL_AND_HAND,
            claimedTileSideways = false,
            addedKan = TableAddedKanPlacement.IN_ROW,
            discardArea = TableDiscardArea.SCATTERED,
        )

        /** 台麻有花牌時每面牌牆的墩數。 */
        const val WALL_STACKS: Int = 18

        /** 每家至少要有的格子數。 */
        const val MIN_CELLS_PER_SEAT: Int = 15

        /** 比較座標的容許誤差。 */
        const val EPSILON: Double = 1e-6
    }
}
