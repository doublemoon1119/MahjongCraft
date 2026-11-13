package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.PublicHandOrder
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.meldDisplayDirection
import kotlin.uuid.Uuid

/**
 * 一位玩家牌區每張牌的最終位置：立牌、摸牌位、副露與移出的牌。
 *
 * 只依桌子位置、座位、規則的桌面牌位配置與牌區內容計算，不涉及動畫與世界物件；呈現端照這份結果擺放與播放動畫。手牌平移、
 * 副露與移出牌列都在這裡計算，所有需要手牌位置的呈現都使用 [handShift]，自動測試也用同一份結果檢查牌與牌之間不會重疊。
 */
object MahjongPlayerAreaLayout {
    /**
     * 計算一位玩家牌區的最終位置。
     *
     * @param input 桌子位置、座位、配置與牌區內容。
     * @return 每張牌的最終位置。
     */
    fun compute(input: MahjongPlayerAreaLayoutInput): MahjongPlayerAreaLayoutResult {
        val plan = plan(
            tileLayout = input.tileLayout,
            wallStacksPerSide = input.wallStacksPerSide,
            cornerWidth = input.cornerWidth,
            melds = input.melds,
            setAsideTileCount = input.setAsideTileIds.size,
            standingTileCount = input.standingTileIds.size,
            playerCount = input.seatLayout.playerCount,
        )
        // 只有全部立牌已公開平放時才使用玩家牌序；仍有隱藏或直立牌時不洩漏私人排序。還沒理過的牌依補進順序接在最右邊。
        val faceUpIds = input.tilePoseStates.filter { it.pose == TilePose.FACE_UP }.mapTo(mutableSetOf()) { it.tileId }
        val unsorted = input.unsortedTileIds.filter { it in input.standingTileIds }
        val standingOrder = if (input.standingTileIds.all { it in faceUpIds }) {
            input.standingTileIds
        } else {
            PublicHandOrder.arrange(input.standingTileIds - unsorted.toSet()) + unsorted
        }
        val standing = standingOrder.mapIndexed { orderIndex, tileId ->
            MahjongAreaTilePlacement(
                tileId = tileId,
                placement = MahjongTileTableLayout.handPlacement(
                    controllerX = input.controllerX,
                    controllerY = input.controllerY,
                    controllerZ = input.controllerZ,
                    tableFacing = input.tableFacing,
                    seatLayout = input.seatLayout,
                    seatIndex = input.seatIndex,
                    handSize = input.standingTileIds.size,
                    tileIndex = input.standingTileIds.size - 1 - orderIndex,
                    handShift = plan.handShift,
                ),
                footprint = if (input.tilePoseStates.any { it.tileId == tileId && it.pose != TilePose.UPRIGHT }) MahjongTileFootprint.LYING else MahjongTileFootprint.STANDING,
                alongOffsetFromCorner = null,
            )
        }
        val drawnSlot = MahjongTileTableLayout.drawnTilePlacement(
            controllerX = input.controllerX,
            controllerY = input.controllerY,
            controllerZ = input.controllerZ,
            tableFacing = input.tableFacing,
            seatLayout = input.seatLayout,
            seatIndex = input.seatIndex,
            standingTileCount = input.standingTileIds.size,
            handShift = plan.handShift,
        )

        fun place(tileId: Uuid, tile: PlannedTile, isFaceDown: Boolean) = MahjongAreaTilePlacement(
            tileId = tileId,
            placement = MahjongTileTableLayout.areaTilePlacement(
                controllerX = input.controllerX,
                controllerY = input.controllerY,
                controllerZ = input.controllerZ,
                tableFacing = input.tableFacing,
                seatLayout = input.seatLayout,
                seatIndex = input.seatIndex,
                x = tile.x,
                z = tile.z,
                isSidewaysTile = tile.isSideways,
            ),
            footprint = if (tile.isSideways) MahjongTileFootprint.LYING_SIDEWAYS else MahjongTileFootprint.LYING,
            alongOffsetFromCorner = MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - tile.x,
            isFaceDown = isFaceDown,
        )

        val melds = input.melds.mapIndexed { meldIndex, meld ->
            plan.meldTiles[meldIndex].map { tile ->
                val tileId = requireNotNull(tile.tileId) { "Meld tiles carry their identifiers" }
                place(tileId, tile, meld.isFaceDown(tileId))
            }
        }
        val setAside = input.setAsideTileIds.mapIndexed { index, tileId -> place(tileId, plan.setAsideTiles[index], isFaceDown = false) }
        return MahjongPlayerAreaLayoutResult(
            handShift = plan.handShift,
            standing = standing,
            drawnSlot = drawnSlot,
            melds = melds,
            setAside = setAside,
        )
    }

    /**
     * 立牌與摸牌位整排的平移；需要手牌位置但不需要整個牌區的呈現（例如發牌、胡牌、流局攤牌）用它，與 [compute] 結果一致。
     *
     * 摸牌位一律預留，不論目前有沒有摸到的牌，所以摸牌與打牌時手牌不移動，只有立牌張數、副露或移出的牌改變時才重新平移。
     *
     * @param tileLayout 該規則的桌面牌位配置。
     * @param wallStacksPerSide 牌牆每面的墩數；沒有實體牌牆時為 null。
     * @param cornerWidth 桌角物件佔用的寬度。
     * @param melds 依宣告順序排列的副露。
     * @param setAsideTileCount 接在副露後面的移出牌張數。
     * @param standingTileCount 立牌張數，不含摸牌位。
     * @param playerCount 本局玩家人數，決定被鳴的牌橫放的位置。
     * @return 往玩家左手邊平移的距離，負值往右手邊。
     */
    fun handShift(
        tileLayout: TableTileLayoutProfile,
        wallStacksPerSide: Int?,
        cornerWidth: Double,
        melds: List<MahjongMeldTileGroup>,
        setAsideTileCount: Int,
        standingTileCount: Int,
        playerCount: Int,
    ): Double = plan(tileLayout, wallStacksPerSide, cornerWidth, melds, setAsideTileCount, standingTileCount, playerCount).handShift

    /**
     * 依配置排出副露、移出的牌與手牌平移。
     *
     * @return 以局部座標表示的排法。
     */
    @Suppress("LongParameterList")
    private fun plan(
        tileLayout: TableTileLayoutProfile,
        wallStacksPerSide: Int?,
        cornerWidth: Double,
        melds: List<MahjongMeldTileGroup>,
        setAsideTileCount: Int,
        standingTileCount: Int,
        playerCount: Int,
    ): AreaPlan {
        val handBand = MahjongTileTableLayout.handRowBand()
        val handSpan = MahjongTileTableLayout.rowSpan(tileLayout, wallStacksPerSide, handBand.left, handBand.right)
        val groups = melds.map { meld -> meldGroup(meld, tileLayout, playerCount) } +
            listOfNotNull(List(setAsideTileCount) { PlannedSlot(tileId = null, isSideways = false) }.takeIf { it.isNotEmpty() })
        return when (tileLayout.meldArea) {
            TableMeldArea.CORNER -> cornerPlan(groups, melds.size, cornerWidth, standingTileCount, handSpan)
            TableMeldArea.BETWEEN_WALL_AND_HAND -> frontPlan(groups, melds.size, tileLayout, wallStacksPerSide, standingTileCount, handSpan)
        }
    }

    /**
     * 桌角副露：從桌角往手牌方向排開，貼齊桌緣；手牌碰到桌角區時往左讓。
     *
     * @return 以局部座標表示的排法。
     */
    @Suppress("LongParameterList")
    private fun cornerPlan(
        groups: List<List<PlannedSlot>>,
        meldCount: Int,
        cornerWidth: Double,
        standingTileCount: Int,
        handSpan: RowSpan,
    ): AreaPlan {
        var cursor = MahjongTileTableLayout.meldStartOffset(cornerWidth)
        val placed = groups.mapIndexed { index, group ->
            if (index > 0) cursor += MahjongTileTableLayout.MELD_GROUP_GAP
            val tiles = mutableListOf<PlannedTile>()
            val addedBeside = mutableListOf<PlannedTile>()
            var sidewaysCursor: Double? = null
            group.forEach { slot ->
                if (slot.isAddedBeside) {
                    // 加槓補上的牌疊在原本橫放牌靠桌子中心那一側，不佔用額外的橫向格位，排在該組最後。
                    addedBeside += cornerTile(slot, requireNotNull(sidewaysCursor) { "An added kan needs a sideways tile" }, MahjongTileTableLayout.ADDED_KAN_DEPTH_OFFSET)
                    return@forEach
                }
                val halfWidth = slot.alongWidth / 2.0
                cursor += halfWidth
                tiles += cornerTile(slot, cursor, 0.0)
                if (slot.isSideways) sidewaysCursor = cursor
                cursor += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
            }
            tiles + addedBeside
        }
        val reservedCornerWidth = if (groups.isEmpty()) cornerWidth else cursor
        val rightLimit = if (reservedCornerWidth > 0.0) {
            MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - reservedCornerWidth - MahjongTileTableLayout.HAND_CORNER_GAP
        } else {
            Double.POSITIVE_INFINITY
        }
        return AreaPlan(
            handShift = MahjongTileTableLayout.fitHandShift(standingTileCount, RESERVE_DRAWN_SLOT, handSpan.left, rightLimit),
            meldTiles = placed.take(meldCount),
            setAsideTiles = placed.drop(meldCount).flatten(),
        )
    }

    /**
     * 牌牆與手牌之間的副露：先宣告的在右邊，整列對齊立牌（不含摸牌位）的中心，超出可用範圍時往內收；碰到左邊牌牆前先縮小組間縫隙，
     * 仍放不下的部分放到手牌那一列的右手邊貼齊桌緣，手牌往左讓。
     *
     * @return 以局部座標表示的排法。
     */
    @Suppress("LongParameterList")
    private fun frontPlan(
        groups: List<List<PlannedSlot>>,
        meldCount: Int,
        tileLayout: TableTileLayoutProfile,
        wallStacksPerSide: Int?,
        standingTileCount: Int,
        handSpan: RowSpan,
    ): AreaPlan {
        val rowZ = MahjongTileTableLayout.FRONT_MELD_ROW_Z
        val halfDepth = MahjongTileDimensions.TILE_HEIGHT / 2.0
        val frontSpan = MahjongTileTableLayout.rowSpan(tileLayout, wallStacksPerSide, rowZ - halfDepth, rowZ + halfDepth)
        val edgeZ = MahjongTileTableLayout.EDGE_ROW_Z
        val edgeSpan = MahjongTileTableLayout.rowSpan(tileLayout, wallStacksPerSide, edgeZ - halfDepth, edgeZ + halfDepth)
        if (wallStacksPerSide != null) {
            val ownWallOuterFace = MahjongTileTableLayout.wallOuterFace(wallStacksPerSide, tileLayout)
            check(ownWallOuterFace < rowZ - halfDepth) {
                "The wall must move toward the centre far enough to leave room for melds between the wall and the hand"
            }
        }
        // 移出的牌每張各自成為一項，放不下時可以只移走後面幾張；副露整組移動，不拆開。
        val items = groups.take(meldCount).map { it to true } + groups.drop(meldCount).flatten().map { listOf(it) to false }
        fun width(item: List<PlannedSlot>) = item.sumOf { it.alongWidth + MahjongTileDimensions.TILE_SMALL_PADDING }
        fun gapBefore(index: Int, gap: Double) = if (index == 0) {
            0.0
        } else if (items[index].second || items[index - 1].second || index == meldCount) {
            gap
        } else {
            0.0
        }
        fun totalWidth(count: Int, gap: Double) = (0 until count).sumOf { width(items[it].first) + gapBefore(it, gap) }

        val available = frontSpan.right - frontSpan.left
        var gap = MahjongTileTableLayout.MELD_GROUP_GAP
        var frontCount = items.size
        if (totalWidth(frontCount, gap) > available) {
            val gapCount = (1 until items.size).count { gapBefore(it, 1.0) > 0.0 }
            val excess = totalWidth(frontCount, gap) - available
            gap = if (gapCount > 0) (gap - excess / gapCount).coerceAtLeast(MahjongTileDimensions.TILE_SMALL_PADDING) else gap
            while (frontCount > 0 && totalWidth(frontCount, gap) > available) frontCount--
        }

        val overflows = frontCount < items.size
        // 全部放得下時整列對齊立牌的中心，超出可用範圍時往內收；放不下時整列已經塞滿，從右端排起。
        // 中心不計摸牌位；摸牌位一律預留，摸牌與打牌時立牌與副露都不移動。
        val unblockedHandShift = MahjongTileTableLayout.fitHandShift(standingTileCount, RESERVE_DRAWN_SLOT, handSpan.left, handSpan.right)
        val standingCentre = MahjongTileTableLayout.handRowEdges(standingTileCount, false).let { (it.left + it.right) / 2.0 } - unblockedHandShift
        // 每張牌之後都算了一段間距，最後一張之後的不屬於牌列本身。
        val rowWidth = totalWidth(frontCount, gap) - if (frontCount > 0) MahjongTileDimensions.TILE_SMALL_PADDING else 0.0
        val planned = MutableList(items.size) { emptyList<PlannedTile>() }
        // 剛好塞滿時兩個界線可能因浮點誤差交錯，以右界為準。
        var cursor = if (overflows) frontSpan.right else minOf(frontSpan.right, maxOf(frontSpan.left + rowWidth, standingCentre + rowWidth / 2.0))
        (0 until frontCount).forEach { index ->
            cursor -= gapBefore(index, gap)
            planned[index] = items[index].first.reversed().map { slot ->
                val tile = PlannedTile(slot.tileId, cursor - slot.alongWidth / 2.0, rowZ, slot.isSideways)
                cursor -= slot.alongWidth + MahjongTileDimensions.TILE_SMALL_PADDING
                tile
            }.reversed()
        }
        var overflowCursor = edgeSpan.right
        (frontCount until items.size).forEach { index ->
            if (index > frontCount) overflowCursor -= gapBefore(index, gap)
            planned[index] = items[index].first.reversed().map { slot ->
                val tile = PlannedTile(slot.tileId, overflowCursor - slot.alongWidth / 2.0, edgeZ - slot.depthOffset(), slot.isSideways)
                overflowCursor -= slot.alongWidth + MahjongTileDimensions.TILE_SMALL_PADDING
                tile
            }.reversed()
        }
        val handShift = if (overflows) {
            MahjongTileTableLayout.fitHandShift(standingTileCount, RESERVE_DRAWN_SLOT, handSpan.left, overflowCursor - MahjongTileTableLayout.HAND_CORNER_GAP)
        } else {
            unblockedHandShift
        }
        return AreaPlan(
            handShift = handShift,
            meldTiles = planned.take(meldCount),
            setAsideTiles = planned.drop(meldCount).flatten(),
        )
    }

    /**
     * 桌角副露中的一張牌，貼齊桌緣。
     *
     * @param slot 這張牌的擺法。
     * @param alongOffsetFromCorner 牌中心距離桌角錨點的距離。
     * @param depthOffset 往桌心額外推的距離。
     * @return 局部座標。
     */
    private fun cornerTile(slot: PlannedSlot, alongOffsetFromCorner: Double, depthOffset: Double): PlannedTile {
        val halfDepth = if (slot.isSideways) MahjongTileDimensions.TILE_WIDTH / 2.0 else MahjongTileDimensions.TILE_HEIGHT / 2.0
        return PlannedTile(
            tileId = slot.tileId,
            x = MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - alongOffsetFromCorner,
            z = MahjongTileTableLayout.MELD_NEAR_EDGE_LINE - halfDepth - depthOffset,
            isSideways = slot.isSideways || slot.isAddedBeside,
        )
    }

    /**
     * 一組副露依配置由右到左擺放的每張牌。
     *
     * 被鳴的牌橫放時依來源方向決定位置；加槓補上的牌依配置疊在橫放牌旁或接在同一列最右邊。
     *
     * @param meld 副露。
     * @param tileLayout 該規則的桌面牌位配置。
     * @param playerCount 本局玩家人數。
     * @return 由右到左的擺法；疊放的加槓牌緊接在它所靠的橫放牌之後。
     */
    private fun meldGroup(
        meld: MahjongMeldTileGroup,
        tileLayout: TableTileLayoutProfile,
        playerCount: Int,
    ): List<PlannedSlot> {
        val addedBeside = meld.type == MeldType.AddedKan && tileLayout.addedKan == TableAddedKanPlacement.BESIDE_CLAIMED_TILE
        val addedTileId = if (addedBeside) meld.tileIds.last() else null
        val rowTileIds = if (addedTileId != null) meld.tileIds.dropLast(1) else meld.tileIds
        val sidewaysTileId = meld.calledTileId?.takeIf { tileLayout.claimedTileSideways }
        val sidewaysSlot = sidewaysTileId?.let {
            MahjongTileTableLayout.sidewaysSlotIndex(meldDisplayDirection(meld.sourceDirection, playerCount), rowTileIds.size)
        }
        val remaining = ArrayDeque(rowTileIds.filterNot { it == sidewaysTileId })
        val tileAtSlot = rowTileIds.indices.map { slot -> if (slot == sidewaysSlot) requireNotNull(sidewaysTileId) else remaining.removeFirst() }
        return buildList {
            for (slot in rowTileIds.indices.reversed()) {
                add(PlannedSlot(tileAtSlot[slot], isSideways = slot == sidewaysSlot))
                if (slot == sidewaysSlot && addedTileId != null) add(PlannedSlot(addedTileId, isSideways = false, isAddedBeside = true))
            }
        }.let { slots -> if (tileLayout.meldArea == TableMeldArea.CORNER) slots else slots.reversed() }
    }

    /** 這張副露牌是否蓋著擺放，依副露的逐牌描述判定，不以副露種類推測。 */
    private fun MahjongMeldTileGroup.isFaceDown(tileId: Uuid): Boolean = allTilesFaceDown || tileId in faceDownTileIds

    /** 計算手牌平移時一律預留摸牌位，摸牌與打牌時手牌不移動。 */
    private const val RESERVE_DRAWN_SLOT: Boolean = true
}

/**
 * 一張牌在牌列中的擺法。
 *
 * @property tileId 牌識別碼；移出的牌在排法階段只用張數，為 null。
 * @property isSideways 是否橫放。
 * @property isAddedBeside 是否為疊在橫放牌旁的加槓牌，不佔用橫向格位。
 */
private data class PlannedSlot(
    val tileId: Uuid?,
    val isSideways: Boolean,
    val isAddedBeside: Boolean = false,
) {
    /** 沿排列方向佔用的寬度。 */
    val alongWidth: Double get() = if (isSideways) MahjongTileDimensions.TILE_HEIGHT else MahjongTileDimensions.TILE_WIDTH

    /**
     * 貼齊桌緣時，橫放牌比直立牌少佔的深度，讓兩種朝向的外緣對齊同一條線。
     *
     * @return 往桌緣方向的修正量（負值）。
     */
    fun depthOffset(): Double = if (isSideways) (MahjongTileDimensions.TILE_WIDTH - MahjongTileDimensions.TILE_HEIGHT) / 2.0 else 0.0
}

/**
 * 一張牌的局部座標。
 *
 * @property tileId 牌識別碼；移出的牌為 null。
 * @property x 局部 X，往玩家右手邊為正。
 * @property z 局部 Z，往玩家方向為正。
 * @property isSideways 是否橫放。
 */
private data class PlannedTile(
    val tileId: Uuid?,
    val x: Double,
    val z: Double,
    val isSideways: Boolean,
)

/**
 * 一位玩家牌區的排法。
 *
 * @property handShift 立牌與摸牌位往玩家左手邊平移的距離，負值往右手邊。
 * @property meldTiles 依宣告順序排列的副露，每組的牌依擺放順序排列。
 * @property setAsideTiles 依移出順序排列的移出牌。
 */
private data class AreaPlan(
    val handShift: Double,
    val meldTiles: List<List<PlannedTile>>,
    val setAsideTiles: List<PlannedTile>,
)
