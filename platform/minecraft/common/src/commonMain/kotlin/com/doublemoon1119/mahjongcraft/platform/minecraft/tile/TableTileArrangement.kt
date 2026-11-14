package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
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
    /**
     * 計算整桌每張牌的擺放。
     *
     * @param input 桌子位置與桌況。
     * @return 依牌識別碼索引的擺放；沒有實體牌牆時不含牌牆與保留區的牌。
     */
    fun arrange(input: TableTileArrangementInput): Map<Uuid, ArrangedTile> {
        val state = input.tableState
        val result = linkedMapOf<Uuid, ArrangedTile>()
        val wallPlacements = wallTiles(input)
        wallPlacements.forEach { result[it.tileId] = it }
        val handPoses = input.handPoseStates.associate { it.tileId to it.pose }
        val scatteredDiscards = if (input.tileLayout.discardArea == TableDiscardArea.SCATTERED) {
            val entryByTileId = state.players.withIndex().flatMap { (seatIndex, player) ->
                player.discardPile.entries.map { entry -> entry.tile.id to ScatteredDiscardEntry(entry.tile.id, seatIndex, entry.isTaken) }
            }.toMap()
            MahjongTileTableLayout.scatteredDiscardPlacements(
                controllerX = input.controllerX,
                controllerY = input.controllerY,
                controllerZ = input.controllerZ,
                tableFacing = input.tableFacing,
                seatLayout = input.seatLayout,
                tileLayout = input.tileLayout,
                wallStacksPerSide = input.wallProjection?.stacksPerSide,
                tableId = state.id,
                roundSerial = state.roundSerial,
                discards = state.discardOrder.map(entryByTileId::getValue),
            )
        } else {
            emptyMap()
        }
        state.players.forEachIndexed { seatIndex, player ->
            val melds = player.hand.melds.map { it.toPresentation(state.config.revealsClosedKanTiles).toTileGroup() }
            val setAsideTiles = player.tileAreas.areas.flatMap { area -> area.tiles.map { it to area.id } }
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
                    standingTileIds = player.hand.tiles.map { it.id },
                    drawnTileId = player.hand.lastDrawn?.id,
                    melds = melds,
                    setAsideTileIds = setAsideTiles.map { it.first.id },
                    cornerWidth = input.cornerWidthBySeat[seatIndex] ?: 0.0,
                    tilePoseStates = input.handPoseStates,
                    unsortedTileIds = input.unsortedTileIds,
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
            player.hand.lastDrawn?.let { drawn ->
                result[drawn.id] = ArrangedTile(drawn.id, TileRowKey(TileRegion.DRAWN, player.id, null), 0, layout.drawnSlot, handPoses[drawn.id] ?: TilePose.UPRIGHT)
            }
            layout.melds.forEachIndexed { meldIndex, placements ->
                val row = TileRowKey(TileRegion.MELD, player.id, "meld:$meldIndex")
                placements.forEachIndexed { order, placed ->
                    result[placed.tileId] = ArrangedTile(placed.tileId, row, order, placed.placement, if (placed.isFaceDown) TilePose.FACE_DOWN else TilePose.FACE_UP)
                }
            }
            val areaByTileId = setAsideTiles.associate { (tile, areaId) -> tile.id to areaId }
            layout.setAside.forEachIndexed { order, placed ->
                val row = TileRowKey(TileRegion.SET_ASIDE, player.id, areaByTileId.getValue(placed.tileId))
                result[placed.tileId] = ArrangedTile(placed.tileId, row, order, placed.placement, TilePose.FACE_UP)
            }
            if (input.tileLayout.discardArea == TableDiscardArea.SCATTERED) {
                // 散放的位置依全桌先打出的牌決定，被鳴走的牌也計入，其他牌不會因此移動。
                val discardRow = TileRowKey(TileRegion.DISCARD, player.id, null)
                player.discardPile.entries.filterNot { it.isTaken }.forEachIndexed { order, entry ->
                    val tileId = entry.tile.id
                    result[tileId] = ArrangedTile(tileId, discardRow, order, scatteredDiscards.getValue(tileId), TilePose.FACE_UP)
                }
                return@forEachIndexed
            }
            val discards = player.discardPile.entries.filterNot { it.isTaken }.map { it.tile.id }
            val sidewaysTileId = (player.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId()
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
     * @param input 桌子位置與桌況。
     * @return 牌牆與保留區的牌；沒有實體牌牆時為空清單。
     */
    private fun wallTiles(input: TableTileArrangementInput): List<ArrangedTile> {
        val projection = input.wallProjection ?: return emptyList()
        val placements = input.tableState.physicalWallLayout?.placements ?: return emptyList()
        val wall = input.tableState.tileWall.getAllTiles().map { it.id to TileRegion.WALL } +
            input.tableState.reservedWallTiles.map { it.id to TileRegion.RESERVED }
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
