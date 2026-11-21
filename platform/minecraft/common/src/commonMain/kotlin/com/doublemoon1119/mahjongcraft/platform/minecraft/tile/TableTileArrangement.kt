package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import kotlin.math.abs
import kotlin.uuid.Uuid

/**
 * 依一份桌況算出整桌每張牌的位置與姿態：牌牆、保留區、立牌、摸牌位、副露、移出的牌與牌河。
 *
 * 只依桌子位置與桌況計算，不涉及動畫與世界物件。呈現時比較前後兩份結果，得出每張牌怎麼移動。
 */
object TableTileArrangement {
    /** 沒有實體牌牆時，排在桌子中央那一排牌的 [TileRowKey.group]。 */
    const val CENTER_ROW_GROUP: String = "center"

    /**
     * 計算整桌每張牌的擺放。
     *
     * @param input 桌子位置與桌況。
     * @return 依牌識別碼索引的擺放；沒有實體牌牆時，牌牆與保留區只含排在桌子中央的公開牌。
     */
    fun arrange(input: TableTileArrangementInput): Map<Uuid, ArrangedTile> {
        val state = input.table
        val result = linkedMapOf<Uuid, ArrangedTile>()
        val wallPlacements = wallTiles(input)
        wallPlacements.forEach { result[it.tileId] = it }
        val handPoses = input.handPoseStates.associate { it.tileId to it.pose }
        val scatteredDiscards = if (input.tileLayout.discardArea == TableDiscardArea.SCATTERED) {
            val entryByTileId = state.players.withIndex().flatMap { (seatIndex, player) ->
                player.discards.map { entry -> entry.tileId to ScatteredDiscardEntry(entry.tileId, seatIndex, entry.isTaken) }
            }.toMap()
            MahjongTileTableLayout.scatteredDiscardPlacements(
                controllerX = input.controllerX,
                controllerY = input.controllerY,
                controllerZ = input.controllerZ,
                tableFacing = input.tableFacing,
                seatLayout = input.seatLayout,
                tileLayout = input.tileLayout,
                wallStacksPerSide = input.wallProjection?.stacksPerSide,
                patternSeed = state.scatterPatternSeed,
                discards = state.discardOrder.map(entryByTileId::getValue),
            )
        } else {
            emptyMap()
        }
        state.players.forEachIndexed { seatIndex, player ->
            val layout = MahjongPlayerAreaLayout.compute(
                input = MahjongPlayerAreaLayoutInput(
                    controllerX = input.controllerX,
                    controllerY = input.controllerY,
                    controllerZ = input.controllerZ,
                    tableFacing = input.tableFacing,
                    seatLayout = input.seatLayout,
                    seatIndex = seatIndex,
                    tileLayout = input.tileLayout,
                    wallClearance = input.wallClearance?.forSeat(input.seatLayout, seatIndex),
                    standingTileIds = player.standingTileIds,
                    drawnTileId = player.drawnTileId,
                    melds = player.melds,
                    setAsideTileIds = player.setAsideTiles.map { it.tileId },
                    cornerWidth = input.cornerWidthBySeat[seatIndex] ?: 0.0,
                    tilePoseStates = input.handPoseStates,
                    unsortedTileIds = input.unsortedTileIds,
                    standingOrderPublic = input.standingOrderPublic,
                ),
            )
            val unsorted = input.unsortedTileIds.toSet()
            val handRow = TileRowKey(TileRegion.HAND, player.id, null)
            layout.standing.forEachIndexed { order, placed ->
                result[placed.tileId] = ArrangedTile(
                    tileId = placed.tileId,
                    row = handRow,
                    orderInRow = order,
                    placement = placed.placement,
                    pose = handPoses[placed.tileId] ?: TilePose.UPRIGHT,
                    unsorted = placed.tileId in unsorted,
                )
            }
            player.drawnTileId?.let { drawnId ->
                result[drawnId] = ArrangedTile(drawnId, TileRowKey(TileRegion.DRAWN, player.id, null), 0, layout.drawnSlot, handPoses[drawnId] ?: TilePose.UPRIGHT)
            }
            layout.melds.forEachIndexed { meldIndex, placements ->
                val row = TileRowKey(TileRegion.MELD, player.id, "meld:$meldIndex")
                placements.forEachIndexed { order, placed ->
                    result[placed.tileId] = ArrangedTile(placed.tileId, row, order, placed.placement, if (placed.isFaceDown) TilePose.FACE_DOWN else TilePose.FACE_UP)
                }
            }
            val areaByTileId = player.setAsideTiles.associate { it.tileId to it.areaId }
            layout.setAside.forEachIndexed { order, placed ->
                val row = TileRowKey(TileRegion.SET_ASIDE, player.id, areaByTileId.getValue(placed.tileId))
                result[placed.tileId] = ArrangedTile(placed.tileId, row, order, placed.placement, TilePose.FACE_UP)
            }
            if (input.tileLayout.discardArea == TableDiscardArea.SCATTERED) {
                // 散放的位置依全桌先打出的牌決定，被鳴走的牌也計入，其他牌不會因此移動。
                val discardRow = TileRowKey(TileRegion.DISCARD, player.id, null)
                player.discards.filterNot { it.isTaken }.forEachIndexed { order, entry ->
                    val tileId = entry.tileId
                    result[tileId] = ArrangedTile(tileId, discardRow, order, scatteredDiscards.getValue(tileId), TilePose.FACE_UP)
                }
                return@forEachIndexed
            }
            val discards = player.discards.filterNot { it.isTaken }.map { it.tileId }
            val sidewaysTileId = player.sidewaysMarkedTileId
            val sidewaysIndex = sidewaysTileId?.let(discards::indexOf)?.takeIf { it >= 0 }
            val seatYaw = MahjongTileTableLayout.seatYaw(input.tableFacing, input.seatLayout, seatIndex)
            val wallRemaining = wallPlacements.any { it.pose == TilePose.FACE_DOWN && yawMatches(it.placement.yaw, seatYaw) }
            val discardRow = TileRowKey(TileRegion.DISCARD, player.id, null)
            discards.forEachIndexed { index, tileId ->
                val placement = MahjongTileTableLayout.discardPlacement(
                    controllerX = input.controllerX,
                    controllerY = input.controllerY,
                    controllerZ = input.controllerZ,
                    tableFacing = input.tableFacing,
                    seatLayout = input.seatLayout,
                    seatIndex = seatIndex,
                    discardIndex = index,
                    isSidewaysMarked = tileId == sidewaysTileId,
                    sidewaysMarkedDiscardIndex = sidewaysIndex,
                    wallRemaining = wallRemaining,
                )
                result[tileId] = ArrangedTile(tileId, discardRow, index, placement, TilePose.FACE_UP)
            }
        }
        return result
    }

    /**
     * 牌牆與保留區每張牌的擺放；實體牌牆格位依 [TableTileArrangementInput.wallProjection] 投影到桌上。
     *
     * 沒有實體牌牆時，只擺正面朝上的公開牌（例如日麻的寶牌指示牌）：依牌山與保留區的順序在桌子中央排成一排，
     * 牌面朝向莊家；其他牌還沒有桌面位置。
     *
     * @param input 桌子位置與桌況。
     * @return 牌牆與保留區有桌面位置的牌。
     */
    private fun wallTiles(input: TableTileArrangementInput): List<ArrangedTile> {
        val wall = input.table.wallTileIds.map { it to TileRegion.WALL } +
            input.table.reservedTileIds.map { it to TileRegion.RESERVED }
        val projection = input.wallProjection
        val placements = input.table.wallPlacements
        if (projection == null || placements == null) return centerRowTiles(input, wall)
        return wall.mapIndexedNotNull { order, (tileId, region) ->
            val placement = placements[tileId] ?: return@mapIndexedNotNull null
            ArrangedTile(
                tileId = tileId,
                row = TileRowKey(region, null, null),
                orderInRow = order,
                placement = projection.project(placement),
                pose = if (tileId in input.faceUpWallTileIds) TilePose.FACE_UP else TilePose.FACE_DOWN,
            )
        }
    }

    /**
     * 沒有實體牌牆時，把正面朝上的公開牌依牌山與保留區順序排在桌子中央。
     *
     * @param input 桌子位置與桌況。
     * @param wall 牌山與保留區依順序排列的牌與所屬區域。
     * @return 中央一排的牌；沒有正面朝上的公開牌時為空清單。
     */
    private fun centerRowTiles(input: TableTileArrangementInput, wall: List<Pair<Uuid, TileRegion>>): List<ArrangedTile> {
        val faceUp = wall.filter { (tileId, _) -> tileId in input.faceUpWallTileIds }
        return faceUp.mapIndexed { index, (tileId, region) ->
            ArrangedTile(
                tileId = tileId,
                row = TileRowKey(region, null, CENTER_ROW_GROUP),
                orderInRow = index,
                placement = MahjongTileTableLayout.centerRowPlacement(
                    controllerX = input.controllerX,
                    controllerY = input.controllerY,
                    controllerZ = input.controllerZ,
                    tableFacing = input.tableFacing,
                    seatLayout = input.seatLayout,
                    seatIndex = input.table.dealerIndex,
                    index = index,
                    count = faceUp.size,
                ),
                pose = TilePose.FACE_UP,
            )
        }
    }

    /** 兩個水平朝向是否相同，容許浮點誤差並把角度換算到同一圈。 */
    private fun yawMatches(a: Float, b: Float): Boolean {
        val normalizedA = ((a % FULL_YAW_DEGREES) + FULL_YAW_DEGREES) % FULL_YAW_DEGREES
        val normalizedB = ((b % FULL_YAW_DEGREES) + FULL_YAW_DEGREES) % FULL_YAW_DEGREES
        return abs(normalizedA - normalizedB) < YAW_TOLERANCE_DEGREES
    }

    /** 判斷朝向相同的容許誤差。 */
    private const val YAW_TOLERANCE_DEGREES: Float = 0.01f

    /** 一圈的角度。 */
    private const val FULL_YAW_DEGREES: Float = 360.0f
}
