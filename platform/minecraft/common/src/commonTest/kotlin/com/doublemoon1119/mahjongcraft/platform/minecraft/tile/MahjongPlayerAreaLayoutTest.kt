package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.PublicHandOrder
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

/** 以牌區的最終位置驗證牌與牌、牌與桌角物件之間不會重疊。 */
class MahjongPlayerAreaLayoutTest {
    /** 完整平放手牌沿用玩家順序，未攤牌的共享布局維持公開順序。 */
    @Test
    fun `fully face up hand keeps player order while concealed hand uses public order`() {
        val ids = List(13) { Uuid.random() }
        val standing = MahjongPlayerAreaLayout.compute(inputForPose(ids, handIsLaidDown = false))
        val laidDown = MahjongPlayerAreaLayout.compute(inputForPose(ids, handIsLaidDown = true))

        assertTrue(standing.standing.all { it.footprint == MahjongTileFootprint.STANDING })
        assertTrue(laidDown.standing.all { it.footprint == MahjongTileFootprint.LYING })
        assertEquals(PublicHandOrder.arrange(ids), standing.standing.map { it.tileId })
        assertEquals(ids, laidDown.standing.map { it.tileId })
    }

    /** 已攤牌的立牌遵循傳入牌序，關閉排序時不自作主張重排；摸牌位不加入立牌列。 */
    @Test
    fun `face up hand respects both supplied sorted and manual orders`() {
        val ids = listOf(
            Uuid.parse("00000000-0000-0000-0000-000000000003"),
            Uuid.parse("00000000-0000-0000-0000-000000000001"),
            Uuid.parse("00000000-0000-0000-0000-000000000002"),
        )
        val drawn = Uuid.random()
        listOf(ids, ids.reversed()).forEach { order ->
            val input = inputForPose(order, handIsLaidDown = true).copy(drawnTileId = drawn)
            val result = MahjongPlayerAreaLayout.compute(input)
            assertEquals(order, result.standing.map { it.tileId })
            assertTrue(result.standing.none { it.tileId == drawn })
        }
    }

    /** 同一手牌可同時有平放與直立牌，不將一張牌的姿態套用整手。 */
    @Test
    fun `mixed hand poses apply only to the selected physical tiles`() {
        val ids = List(4) { Uuid.random() }
        val input = inputForPose(ids, handIsLaidDown = false).copy(
            tilePoseStates = listOf(TilePoseState(ids.first(), TilePose.FACE_UP)),
        )
        val layout = MahjongPlayerAreaLayout.compute(input)
        assertEquals(PublicHandOrder.arrange(ids), layout.standing.map { it.tileId })
        assertTrue(layout.standing.single { it.tileId == ids.first() }.footprint == MahjongTileFootprint.LYING)
        assertTrue(layout.standing.filterNot { it.tileId == ids.first() }.all { it.footprint == MahjongTileFootprint.STANDING })
    }

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

    /** 手牌讓開桌角區時，連同一律預留的摸牌位剛好讓到保留的縫隙，不會讓得太少或太多；不需要讓開時維持置中。 */
    @Test
    fun `yielding hand keeps exactly the corner gap`() {
        forEachLayout { input, layout ->
            val handRight = MahjongTileTableLayout.handRowEdges(input.standingTileIds.size, hasDrawnTile = true).right - layout.handShift
            val cornerTiles = layout.melds.flatten() + layout.setAside
            val cornerReach = maxOf(input.cornerWidth, cornerTiles.maxOfOrNull { tile -> requireNotNull(tile.alongOffsetFromCorner) + tile.halfAlong() } ?: 0.0)
            val gap = MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - cornerReach - handRight
            if (layout.handShift > 0.0) {
                assertTrue(
                    gap >= MahjongTileTableLayout.HAND_CORNER_GAP - EPSILON &&
                        gap <= MahjongTileTableLayout.HAND_CORNER_GAP + 2 * MahjongTileDimensions.TILE_SMALL_PADDING + EPSILON,
                    "hand leaves $gap before the corner area in $input",
                )
            } else {
                assertEquals(0.0, layout.handShift, EPSILON)
                assertTrue(gap >= MahjongTileTableLayout.HAND_CORNER_GAP - EPSILON || cornerReach == 0.0, "centred hand reaches the corner area in $input")
            }
        }
    }

    /** 標準配置下，日麻的實體牌牆不會碰到手牌與桌角副露，有沒有牌牆算出的擺法完全相同。 */
    @Test
    fun `standard walls never move the riichi player area`() {
        forEachLayout { input, layout ->
            listOf(17, 18).forEach { stacks ->
                assertEquals(layout, MahjongPlayerAreaLayout.compute(input.copy(wallStacksPerSide = stacks)), "walls of $stacks stacks moved $input")
            }
        }
    }

    /**
     * 台麻的副露放在牌牆與手牌之間：各種副露、花牌張數、摸牌位與手牌是否推倒下，牌區內的牌彼此不重疊，也不碰到任何一面牌牆；
     * 副露那一列放不下的牌移到手牌那一列的右手邊，手牌往左讓。
     */
    @Test
    fun `taiwan player areas never overlap each other or the walls`() {
        val tileLayout = taiwanTileLayout()
        val seatLayout = MahjongTableSeatLayout.forPlayerCount(4)
        var overflowSeen = false
        MahjongTableFacing.entries.forEach { tableFacing ->
            val walls = wallFootprints(tableFacing, tileLayout)
            (0 until 4).forEach { seatIndex ->
                TAIWAN_MELD_PATTERNS.forEach { pattern ->
                    (0..pattern.size).forEach { meldCount ->
                        (0..TAIWAN_FLOWER_COUNT).forEach { flowerCount ->
                            listOf(false, true).forEach { hasDrawnTile ->
                                listOf(false, true).forEach { handIsLaidDown ->
                                    val ids = List(TAIWAN_HAND_SIZE - meldCount * MELD_HAND_TILES) { Uuid.random() }
                                    val drawn = Uuid.random().takeIf { hasDrawnTile }
                                    val input = MahjongPlayerAreaLayoutInput(
                                        controllerX = 0,
                                        controllerY = 0,
                                        controllerZ = 0,
                                        tableFacing = tableFacing,
                                        seatLayout = seatLayout,
                                        seatIndex = seatIndex,
                                        tileLayout = tileLayout,
                                        wallStacksPerSide = TAIWAN_WALL_STACKS,
                                        standingTileIds = ids,
                                        drawnTileId = drawn,
                                        melds = pattern.take(meldCount).map { it.create() },
                                        setAsideTileIds = List(flowerCount) { Uuid.random() },
                                        cornerWidth = 0.0,
                                        tilePoseStates = if (handIsLaidDown) (ids + listOfNotNull(drawn)).map { TilePoseState(it, TilePose.FACE_UP) } else emptyList(),
                                    )
                                    val layout = MahjongPlayerAreaLayout.compute(input)
                                    val footprints = footprints(input, layout)
                                    footprints.forEachIndexed { index, first ->
                                        footprints.drop(index + 1).forEach { second ->
                                            if (first.overlaps(second)) fail("${first.label} overlaps ${second.label} in $input")
                                        }
                                        walls.forEach { wall -> if (first.overlaps(wall)) fail("${first.label} overlaps ${wall.label} in $input") }
                                    }
                                    val rowTiles = layout.melds.flatten() + layout.setAside
                                    val offsets = rowTiles.map { requireNotNull(it.alongOffsetFromCorner) }
                                    // 副露列與手牌列右手邊各有一張牌落在同一個沿排列方向的位置，代表有牌移到手牌那一列。
                                    val edgeRow = offsets.filterIndexed { index, offset -> offsets.withIndex().any { (other, value) -> other != index && abs(value - offset) < EPSILON } }
                                    if (edgeRow.isNotEmpty()) overflowSeen = true
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(overflowSeen, "Expected the extreme combinations to move some melds next to the hand")
    }

    /** 台麻放得下的副露與花牌整列對齊立牌的中心，摸牌不移動副露，先宣告的在右邊。 */
    @Test
    fun `taiwan meld row is centred on the hand`() {
        val pon = MeldSpec(MeldType.Pon, RelativeDirection.Left, 3).create()
        val chi = MeldSpec(MeldType.Chi, RelativeDirection.Left, 3).create()
        listOf(false, true).forEach { hasDrawnTile ->
            val input = MahjongPlayerAreaLayoutInput(
                controllerX = 0,
                controllerY = 0,
                controllerZ = 0,
                tableFacing = MahjongTableFacing.NORTH,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(4),
                seatIndex = 0,
                tileLayout = taiwanTileLayout(),
                wallStacksPerSide = TAIWAN_WALL_STACKS,
                standingTileIds = List(10) { Uuid.random() },
                drawnTileId = Uuid.random().takeIf { hasDrawnTile },
                melds = listOf(pon, chi),
                setAsideTileIds = List(2) { Uuid.random() },
                cornerWidth = 0.0,
            )
            val layout = MahjongPlayerAreaLayout.compute(input)
            val rowXs = (layout.melds.flatten() + layout.setAside).map { MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - requireNotNull(it.alongOffsetFromCorner) }
            val rowCentre = (rowXs.min() + rowXs.max()) / 2.0
            val handEdges = MahjongTileTableLayout.handRowEdges(input.standingTileIds.size, false)
            val standingCentre = (handEdges.left + handEdges.right) / 2.0 - MahjongPlayerAreaLayout.compute(input.copy(drawnTileId = null)).handShift

            assertEquals(standingCentre, rowCentre, EPSILON)
            assertEquals(layout.melds, MahjongPlayerAreaLayout.compute(input.copy(drawnTileId = null)).melds, "Drawing a tile must not move the melds")
            val ponXs = layout.melds[0].map { MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - requireNotNull(it.alongOffsetFromCorner) }
            val chiXs = layout.melds[1].map { MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - requireNotNull(it.alongOffsetFromCorner) }
            assertTrue(ponXs.min() > chiXs.max(), "Expected the first meld on the right")
        }
    }

    /** 摸牌位一律預留：日麻各種桌角副露與台麻的牌區，摸牌前後立牌、副露與移出的牌都不移動。 */
    @Test
    fun `drawing a tile moves nothing in the player area`() {
        forEachLayout { input, layout ->
            val withoutDrawn = MahjongPlayerAreaLayout.compute(input.copy(drawnTileId = null))
            assertEquals(withoutDrawn.handShift, layout.handShift, EPSILON, "Drawing moved the hand in $input")
            assertEquals(withoutDrawn.standing, layout.standing)
            assertEquals(withoutDrawn.melds, layout.melds)
            assertEquals(withoutDrawn.setAside, layout.setAside)
        }
        TAIWAN_MELD_PATTERNS.forEach { pattern ->
            (0..pattern.size).forEach { meldCount ->
                (0..TAIWAN_FLOWER_COUNT).forEach { flowerCount ->
                    val input = MahjongPlayerAreaLayoutInput(
                        controllerX = 0,
                        controllerY = 0,
                        controllerZ = 0,
                        tableFacing = MahjongTableFacing.NORTH,
                        seatLayout = MahjongTableSeatLayout.forPlayerCount(4),
                        seatIndex = 0,
                        tileLayout = taiwanTileLayout(),
                        wallStacksPerSide = TAIWAN_WALL_STACKS,
                        standingTileIds = List(TAIWAN_HAND_SIZE - meldCount * MELD_HAND_TILES) { Uuid.random() },
                        drawnTileId = Uuid.random(),
                        melds = pattern.take(meldCount).map { it.create() },
                        setAsideTileIds = List(flowerCount) { Uuid.random() },
                        cornerWidth = 0.0,
                    )
                    val drawn = MahjongPlayerAreaLayout.compute(input)
                    val withoutDrawn = MahjongPlayerAreaLayout.compute(input.copy(drawnTileId = null))

                    assertEquals(withoutDrawn.handShift, drawn.handShift, EPSILON, "Drawing moved the hand in $input")
                    assertEquals(withoutDrawn.melds, drawn.melds)
                    assertEquals(withoutDrawn.setAside, drawn.setAside)
                }
            }
        }
    }

    /** 台麻的副露全部直立，加槓四張排成一列。 */
    @Test
    fun `taiwan melds stand upright and an added kan lies in one row`() {
        val addedKan = MeldSpec(MeldType.AddedKan, RelativeDirection.Right, 4).create()
        val input = MahjongPlayerAreaLayoutInput(
            controllerX = 0,
            controllerY = 0,
            controllerZ = 0,
            tableFacing = MahjongTableFacing.NORTH,
            seatLayout = MahjongTableSeatLayout.forPlayerCount(4),
            seatIndex = 0,
            tileLayout = taiwanTileLayout(),
            wallStacksPerSide = TAIWAN_WALL_STACKS,
            standingTileIds = List(13) { Uuid.random() },
            drawnTileId = null,
            melds = listOf(addedKan),
            setAsideTileIds = emptyList(),
            cornerWidth = 0.0,
        )
        val meld = MahjongPlayerAreaLayout.compute(input).melds.single()

        assertTrue(meld.all { it.footprint == MahjongTileFootprint.LYING })
        assertEquals(addedKan.tileIds, meld.map { it.tileId })
        val offsets = meld.map { requireNotNull(it.alongOffsetFromCorner) }
        assertTrue(offsets.zipWithNext().all { (left, right) -> left > right }, "Expected the four tiles from left to right in saved order: $offsets")
    }

    /**
     * 四面完整牌牆每張牌的佔用範圍。
     *
     * @param tableFacing 桌子朝向。
     * @param tileLayout 桌面牌位配置。
     * @return 每張牌牆牌的範圍。
     */
    private fun wallFootprints(tableFacing: MahjongTableFacing, tileLayout: TableTileLayoutProfile): List<Footprint> {
        val sideLayout = MahjongTileWallSideLayout.resolve(
            arrangement = TileWallSideArrangement(reference = TileWallSideReference.TABLE, quarterTurns = listOf(0, 3, 2, 1)),
            dealerSide = MahjongTableSide.SOUTH,
        )
        fun place(side: Int, stack: Int) = MahjongTileTableLayout.wallPlacement(
            controllerX = 0,
            controllerY = 0,
            controllerZ = 0,
            tableFacing = tableFacing,
            sideLayout = sideLayout,
            tileLayout = tileLayout,
            stacksPerSide = TAIWAN_WALL_STACKS,
            position = TileWallPosition(side = side, stack = stack, layer = 0),
        )
        return (0 until 4).flatMap { side ->
            val first = place(side, 0)
            val second = place(side, 1)
            val alongIsX = abs(first.x - second.x) > abs(first.z - second.z)
            (0 until TAIWAN_WALL_STACKS).map { stack ->
                Footprint.of("wall $side/$stack", place(side, stack), MahjongTileDimensions.TILE_WIDTH / 2.0, MahjongTileDimensions.TILE_HEIGHT / 2.0, alongIsX)
            }
        }
    }

    /** @return 內建台麻登記的桌面牌位配置。 */
    private fun taiwanTileLayout(): TableTileLayoutProfile = TableTileLayoutProfileRegistryImpl().apply {
        BundledMinecraftMahjongExtensions.all.forEach { it.registerTableTileLayoutProfiles(this) }
    }.profileFor(BuiltInRuleModuleIds.TAIWAN)

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
                                    listOf(false, true).forEach { handIsLaidDown ->
                                        listOf(false, true).forEach { hasDrawnTile ->
                                            val ids = List(INITIAL_HAND_SIZE - meldCount * MELD_HAND_TILES) { Uuid.random() }
                                            val drawn = Uuid.random().takeIf { hasDrawnTile }
                                            val input = MahjongPlayerAreaLayoutInput(
                                                controllerX = 0,
                                                controllerY = 0,
                                                controllerZ = 0,
                                                tableFacing = tableFacing,
                                                seatLayout = seatLayout,
                                                seatIndex = seatIndex,
                                                tileLayout = TableTileLayoutProfile.STANDARD,
                                                wallStacksPerSide = null,
                                                standingTileIds = ids,
                                                drawnTileId = drawn,
                                                melds = pattern.take(meldCount).map { it.create() },
                                                setAsideTileIds = List(setAsideCount) { Uuid.random() },
                                                cornerWidth = cornerWidth,
                                                tilePoseStates = if (handIsLaidDown) (ids + listOfNotNull(drawn)).map { TilePoseState(it, TilePose.FACE_UP) } else emptyList(),
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
    }

    /** 建立姿態測試使用的最小牌區輸入。 */
    private fun inputForPose(ids: List<Uuid>, handIsLaidDown: Boolean): MahjongPlayerAreaLayoutInput = MahjongPlayerAreaLayoutInput(
        controllerX = 0,
        controllerY = 0,
        controllerZ = 0,
        tableFacing = MahjongTableFacing.NORTH,
        seatLayout = MahjongTableSeatLayout.forPlayerCount(4),
        seatIndex = 0,
        tileLayout = TableTileLayoutProfile.STANDARD,
        wallStacksPerSide = null,
        standingTileIds = ids,
        drawnTileId = null,
        melds = emptyList(),
        setAsideTileIds = emptyList(),
        cornerWidth = 0.0,
        tilePoseStates = if (handIsLaidDown) ids.map { TilePoseState(it, TilePose.FACE_UP) } else emptyList(),
    )

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
        val drawnFootprint = layout.drawnSlot.takeIf { (input.drawnTileId != null) }?.let { slot ->
            Footprint.of(
                "drawn slot",
                slot,
                MahjongTileDimensions.TILE_WIDTH / 2.0,
                if (input.tilePoseStates.any { it.tileId == input.drawnTileId && it.pose == TilePose.FACE_UP }) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_DEPTH / 2.0,
                alongIsX,
            )
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
                faceDownTileIds = if (type == MeldType.ClosedKan) setOf(tiles.first(), tiles.last()) else emptySet(),
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

        /** 台麻起始手牌張數。 */
        const val TAIWAN_HAND_SIZE: Int = 16

        /** 台麻最多的花牌張數。 */
        const val TAIWAN_FLOWER_COUNT: Int = 8

        /** 台麻有花牌時每面牌牆的墩數。 */
        const val TAIWAN_WALL_STACKS: Int = 18

        /** 台麻測試的副露順序，最多五組；第一種全是槓。 */
        val TAIWAN_MELD_PATTERNS: List<List<MeldSpec>> = listOf(
            List(5) { index -> if (index % 2 == 0) MeldSpec(MeldType.AddedKan, RelativeDirection.Right, 4) else MeldSpec(MeldType.OpenKan, RelativeDirection.Across, 4) },
            listOf(
                MeldSpec(MeldType.Chi, RelativeDirection.Left, 3),
                MeldSpec(MeldType.Pon, RelativeDirection.Across, 3),
                MeldSpec(MeldType.ClosedKan, RelativeDirection.Self, 4),
                MeldSpec(MeldType.Pon, RelativeDirection.Right, 3),
                MeldSpec(MeldType.AddedKan, RelativeDirection.Left, 4),
            ),
        )

        /** 測試的副露順序；每種順序依序取前幾組。 */
        val MELD_PATTERNS: List<List<MeldSpec>> = listOf(
            listOf(
                MeldSpec(MeldType.Pon, RelativeDirection.Left, 3),
                MeldSpec(MeldType.AddedKan, RelativeDirection.Right, 4),
                MeldSpec(MeldType.ClosedKan, RelativeDirection.Self, 4),
                MeldSpec(MeldType.OpenKan, RelativeDirection.Across, 4),
            ),
            listOf(
                MeldSpec(MeldType.Chi, RelativeDirection.Left, 3),
                MeldSpec(MeldType.Pon, RelativeDirection.Across, 3),
                MeldSpec(MeldType.Pon, RelativeDirection.Right, 3),
                MeldSpec(MeldType.AddedKan, RelativeDirection.Left, 4),
            ),
        )
    }
}
