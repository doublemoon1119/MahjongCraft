package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

/** 以牌區的最終位置驗證牌與牌、牌與桌角物件之間不會重疊。 */
class MahjongPlayerAreaLayoutTest {
    /** 各種人數、座位、桌子朝向、副露組合、移出張數、桌角寬度與摸牌位下，牌區內的牌與桌角區都不互相重疊。 */
    @Test
    fun `player area tiles never overlap each other or the corner props`() {
        forEachLayout { input, layout ->
            val footprints = footprints(input, layout)
            footprints.forEachIndexed { index, first ->
                footprints.drop(index + 1).forEach { second ->
                    if (first.overlaps(second)) fail("${first.label} overlaps ${second.label} in $input")
                }
            }
        }
    }

    /** 副露與移出牌列的實際寬度與手牌讓位使用的桌角區寬度一致，手牌不會讓得太少或太多。 */
    @Test
    fun `corner area width matches the placed melds and set aside tiles`() {
        forEachLayout { input, layout ->
            val cornerTiles = layout.melds.flatten() + layout.setAside
            if (cornerTiles.isEmpty()) return@forEachLayout
            val farthestEdge = cornerTiles.maxOf { tile -> requireNotNull(tile.alongOffsetFromCorner) + tile.halfAlong() }
            val reserved = MahjongTileTableLayout.cornerAreaWidth(input.cornerWidth, input.melds, input.setAsideTileIds.size)

            assertTrue(farthestEdge <= reserved + EPSILON, "placed tiles reach $farthestEdge beyond the reserved $reserved in $input")
            assertTrue(
                reserved - farthestEdge <= MahjongTileDimensions.TILE_SMALL_PADDING + EPSILON,
                "reserved $reserved leaves an unused gap after $farthestEdge in $input",
            )
        }
    }

    /**
     * 逐一產生測試用的牌區並計算位置。
     *
     * @param check 對每個牌區執行的檢查。
     */
    private fun forEachLayout(check: (MahjongPlayerAreaLayoutInput, MahjongPlayerAreaLayoutResult) -> Unit) {
        PLAYER_COUNTS.forEach { playerCount ->
            val seatLayout = MahjongTableSeatLayout.forPlayerCount(playerCount)
            (0 until playerCount).forEach { seatIndex ->
                MahjongTableFacing.entries.forEach { tableFacing ->
                    MELD_PATTERNS.forEach { pattern ->
                        (0..pattern.size).forEach { meldCount ->
                            (0..MAX_SET_ASIDE_TILES).forEach { setAsideCount ->
                                CORNER_WIDTHS.forEach { cornerWidth ->
                                    listOf(false, true).forEach { hasDrawnTile ->
                                        val input = MahjongPlayerAreaLayoutInput(
                                            controllerX = 0,
                                            controllerY = 0,
                                            controllerZ = 0,
                                            tableFacing = tableFacing,
                                            seatLayout = seatLayout,
                                            seatIndex = seatIndex,
                                            standingTileIds = List(INITIAL_HAND_SIZE - meldCount * MELD_HAND_TILES) { Uuid.random() },
                                            hasDrawnTile = hasDrawnTile,
                                            melds = pattern.take(meldCount).map { it.create() },
                                            setAsideTileIds = List(setAsideCount) { Uuid.random() },
                                            cornerWidth = cornerWidth,
                                        )
                                        check(input, MahjongPlayerAreaLayout.compute(input))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 算出牌區每張牌與桌角區在桌面上佔用的範圍。
     *
     * @param input 牌區的輸入。
     * @param layout 牌區的最終位置。
     * @return 每張牌與桌角區的佔用範圍。
     */
    private fun footprints(input: MahjongPlayerAreaLayoutInput, layout: MahjongPlayerAreaLayoutResult): List<Footprint> {
        val alongIsX = rowRunsAlongX(input)
        val tiles = layout.standing + layout.melds.flatten() + layout.setAside
        val tileFootprints = tiles.map { tile -> Footprint.of(tile.tileId.toString(), tile.placement, tile.halfAlong(), tile.halfAcross(), alongIsX) }
        val drawnFootprint = layout.drawnSlot.takeIf { input.hasDrawnTile }?.let { slot ->
            Footprint.of("drawn slot", slot, MahjongTileDimensions.TILE_WIDTH / 2.0, MahjongTileDimensions.TILE_DEPTH / 2.0, alongIsX)
        }
        // 桌角物件佔用從桌角錨點起 cornerWidth 的範圍，橫向與平放的副露同寬。
        val cornerFootprint = input.cornerWidth.takeIf { it > 0.0 }?.let { width ->
            val center = MahjongTileTableLayout.meldPlacement(
                controllerX = input.controllerX,
                controllerY = input.controllerY,
                controllerZ = input.controllerZ,
                tableFacing = input.tableFacing,
                seatLayout = input.seatLayout,
                seatIndex = input.seatIndex,
                alongOffsetFromCorner = width / 2.0,
                isSidewaysTile = false,
            )
            Footprint.of("corner props", center, width / 2.0, MahjongTileDimensions.TILE_HEIGHT / 2.0, alongIsX)
        }
        return tileFootprints + listOfNotNull(drawnFootprint, cornerFootprint)
    }

    /**
     * 判斷這個座位的手牌沿世界 X 軸還是 Z 軸排列。
     *
     * @param input 牌區的輸入。
     * @return 沿世界 X 軸排列時為 true。
     */
    private fun rowRunsAlongX(input: MahjongPlayerAreaLayoutInput): Boolean {
        val (first, second) = (0..1).map { tileIndex ->
            MahjongTileTableLayout.handPlacement(
                controllerX = input.controllerX,
                controllerY = input.controllerY,
                controllerZ = input.controllerZ,
                tableFacing = input.tableFacing,
                seatLayout = input.seatLayout,
                seatIndex = input.seatIndex,
                handSize = 2,
                tileIndex = tileIndex,
            )
        }
        return abs(first.x - second.x) > abs(first.z - second.z)
    }

    /** @return 這張牌沿排列方向的半寬。 */
    private fun MahjongAreaTilePlacement.halfAlong(): Double = when (footprint) {
        MahjongTileFootprint.STANDING, MahjongTileFootprint.LYING -> MahjongTileDimensions.TILE_WIDTH / 2.0
        MahjongTileFootprint.LYING_SIDEWAYS -> MahjongTileDimensions.TILE_HEIGHT / 2.0
    }

    /** @return 這張牌垂直於排列方向的半寬。 */
    private fun MahjongAreaTilePlacement.halfAcross(): Double = when (footprint) {
        MahjongTileFootprint.STANDING -> MahjongTileDimensions.TILE_DEPTH / 2.0
        MahjongTileFootprint.LYING -> MahjongTileDimensions.TILE_HEIGHT / 2.0
        MahjongTileFootprint.LYING_SIDEWAYS -> MahjongTileDimensions.TILE_WIDTH / 2.0
    }

    /**
     * 桌面上的一塊矩形範圍。
     *
     * @property label 失敗訊息使用的名稱。
     * @property minX 世界 X 最小值。
     * @property maxX 世界 X 最大值。
     * @property minZ 世界 Z 最小值。
     * @property maxZ 世界 Z 最大值。
     */
    private data class Footprint(
        val label: String,
        val minX: Double,
        val maxX: Double,
        val minZ: Double,
        val maxZ: Double,
    ) {
        /** @return 兩塊範圍是否重疊超過容許誤差；只碰到邊不算重疊。 */
        fun overlaps(other: Footprint): Boolean = minX < other.maxX - EPSILON &&
            other.minX < maxX - EPSILON &&
            minZ < other.maxZ - EPSILON &&
            other.minZ < maxZ - EPSILON

        companion object {
            /**
             * 依中心點與沿排列方向、垂直方向的半寬建立範圍。
             *
             * @param label 失敗訊息使用的名稱。
             * @param center 中心點。
             * @param halfAlong 沿排列方向的半寬。
             * @param halfAcross 垂直於排列方向的半寬。
             * @param alongIsX 排列方向是否為世界 X 軸。
             * @return 範圍。
             */
            fun of(
                label: String,
                center: MahjongTileWallPlacement,
                halfAlong: Double,
                halfAcross: Double,
                alongIsX: Boolean,
            ): Footprint {
                val halfX = if (alongIsX) halfAlong else halfAcross
                val halfZ = if (alongIsX) halfAcross else halfAlong
                return Footprint(label, center.x - halfX, center.x + halfX, center.z - halfZ, center.z + halfZ)
            }
        }
    }

    /**
     * 測試用的一種副露。
     *
     * @property type 副露種類。
     * @property direction 鳴取來源方向。
     * @property tileCount 牌張數。
     */
    private data class MeldSpec(val type: MeldType, val direction: RelativeDirection, val tileCount: Int) {
        /** @return 依規格建立的副露；暗槓兩端蓋牌，其餘以第三張為鳴取的牌。 */
        fun create(): MahjongMeldTileGroup {
            val tiles = List(tileCount) { Uuid.random() }
            return MahjongMeldTileGroup(
                type = type,
                tileIds = tiles,
                calledTileId = tiles[2].takeIf { direction != RelativeDirection.Self },
                sourceDirection = direction,
                allTilesFaceDown = false,
                faceDownTileIds = if (type == MeldType.CLOSED_KAN) setOf(tiles.first(), tiles.last()) else emptySet(),
            )
        }
    }

    private companion object {
        /** 兩塊範圍重疊的容許誤差。 */
        const val EPSILON: Double = 1e-6

        /** 測試的人數。 */
        val PLAYER_COUNTS: List<Int> = listOf(3, 4)

        /** 起始手牌張數。 */
        const val INITIAL_HAND_SIZE: Int = 13

        /** 每組副露從手牌拿走的張數，槓的第四張由補牌補回。 */
        const val MELD_HAND_TILES: Int = 3

        /** 測試的最多移出張數。 */
        const val MAX_SET_ASIDE_TILES: Int = 4

        /** 測試的桌角寬度：沒有物件、一根點棒、一整排點棒。 */
        val CORNER_WIDTHS: List<Double> = listOf(0.0, 0.065, 0.26)

        /** 測試的副露順序；每種順序依序取前幾組。 */
        val MELD_PATTERNS: List<List<MeldSpec>> = listOf(
            listOf(
                MeldSpec(MeldType.PON, RelativeDirection.Left, 3),
                MeldSpec(MeldType.ADDED_KAN, RelativeDirection.Right, 4),
                MeldSpec(MeldType.CLOSED_KAN, RelativeDirection.Self, 4),
                MeldSpec(MeldType.OPEN_KAN, RelativeDirection.Across, 4),
            ),
            listOf(
                MeldSpec(MeldType.CHI, RelativeDirection.Left, 3),
                MeldSpec(MeldType.PON, RelativeDirection.Across, 3),
                MeldSpec(MeldType.PON, RelativeDirection.Right, 3),
                MeldSpec(MeldType.ADDED_KAN, RelativeDirection.Left, 4),
            ),
        )
    }
}
