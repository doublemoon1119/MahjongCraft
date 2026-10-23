package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.PublicHandOrder
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.meldDisplayDirection
import kotlin.uuid.Uuid

/**
 * 一位玩家牌區每張牌的最終位置：立牌、摸牌位、副露與移出的牌。
 *
 * 只依桌子位置、座位與牌區內容計算，不涉及動畫與世界物件；呈現端照這份結果擺放與播放動畫。手牌讓位、副露游標與移出牌列
 * 都在這裡計算，自動測試也用同一份結果檢查牌與牌之間不會重疊。
 */
object MahjongPlayerAreaLayout {
    /**
     * 計算一位玩家牌區的最終位置。
     *
     * @param input 桌子位置、座位與牌區內容。
     * @return 每張牌的最終位置。
     */
    fun compute(input: MahjongPlayerAreaLayoutInput): MahjongPlayerAreaLayoutResult {
        val reservedCornerWidth = MahjongTileTableLayout.cornerAreaWidth(
            cornerPropWidth = input.cornerWidth,
            melds = input.melds,
            setAsideTileCount = input.setAsideTileIds.size,
        )
        val cornerYieldShift = MahjongTileTableLayout.handCornerYieldShift(
            handSize = input.standingTileIds.size,
            reservedCornerWidth = reservedCornerWidth,
            hasDrawnTile = input.hasDrawnTile,
        )
        // 世界實體由所有觀察者共用，立牌只依公開順序擺放。
        val standing = PublicHandOrder.arrange(input.standingTileIds).mapIndexed { orderIndex, tileId ->
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
                    cornerYieldShift = cornerYieldShift,
                ),
                footprint = MahjongTileFootprint.STANDING,
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
            cornerYieldShift = cornerYieldShift,
        )

        fun cornerTile(
            tileId: Uuid,
            alongOffsetFromCorner: Double,
            isSideways: Boolean,
            isFaceDown: Boolean,
            depthOffsetFromEdge: Double = 0.0,
        ) = MahjongAreaTilePlacement(
            tileId = tileId,
            placement = MahjongTileTableLayout.meldPlacement(
                controllerX = input.controllerX,
                controllerY = input.controllerY,
                controllerZ = input.controllerZ,
                tableFacing = input.tableFacing,
                seatLayout = input.seatLayout,
                seatIndex = input.seatIndex,
                alongOffsetFromCorner = alongOffsetFromCorner,
                isSidewaysTile = isSideways,
                depthOffsetFromEdge = depthOffsetFromEdge,
            ),
            footprint = if (isSideways) MahjongTileFootprint.LYING_SIDEWAYS else MahjongTileFootprint.LYING,
            alongOffsetFromCorner = alongOffsetFromCorner,
            isFaceDown = isFaceDown,
        )

        var cursorAlong = MahjongTileTableLayout.meldStartOffset(input.cornerWidth)
        val melds = input.melds.mapIndexed { meldIndex, meld ->
            if (meldIndex > 0) cursorAlong += MahjongTileTableLayout.MELD_GROUP_GAP
            val placements = mutableListOf<MahjongAreaTilePlacement>()
            val addedTileId = if (meld.type == MeldType.ADDED_KAN) meld.tileIds.last() else null
            val baseTileIds = if (addedTileId != null) meld.tileIds.dropLast(1) else meld.tileIds
            val slotCount = baseTileIds.size
            val sidewaysSlot = meld.calledTileId?.let {
                MahjongTileTableLayout.sidewaysSlotIndex(meldDisplayDirection(meld.sourceDirection, input.seatLayout.playerCount), slotCount)
            }
            val remainingTileIds = ArrayDeque(baseTileIds.filterNot { it == meld.calledTileId })
            val tileAtSlot = (0 until slotCount).map { slot ->
                if (slot == sidewaysSlot) requireNotNull(meld.calledTileId) else remainingTileIds.removeFirst()
            }
            var sidewaysAlongOffset: Double? = null
            for (slot in slotCount - 1 downTo 0) {
                val isSideways = slot == sidewaysSlot
                val halfWidth = if (isSideways) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_WIDTH / 2.0
                cursorAlong += halfWidth
                placements += cornerTile(tileAtSlot[slot], cursorAlong, isSideways, meld.isFaceDown(tileAtSlot[slot]))
                if (isSideways) sidewaysAlongOffset = cursorAlong
                cursorAlong += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
            }
            if (addedTileId != null) {
                // 加槓補上的牌疊在原本橫放牌靠桌子中心那一側，不佔用額外的橫向格位。
                placements += cornerTile(
                    tileId = addedTileId,
                    alongOffsetFromCorner = requireNotNull(sidewaysAlongOffset) { "An added kan needs a sideways tile" },
                    isSideways = true,
                    isFaceDown = meld.isFaceDown(addedTileId),
                    depthOffsetFromEdge = MahjongTileTableLayout.ADDED_KAN_DEPTH_OFFSET,
                )
            }
            placements.toList()
        }

        if (input.setAsideTileIds.isNotEmpty() && input.melds.isNotEmpty()) cursorAlong += MahjongTileTableLayout.MELD_GROUP_GAP
        val setAside = input.setAsideTileIds.map { tileId ->
            cursorAlong += MahjongTileDimensions.TILE_WIDTH / 2.0
            cornerTile(tileId, cursorAlong, isSideways = false, isFaceDown = false).also {
                cursorAlong += MahjongTileDimensions.TILE_WIDTH / 2.0 + MahjongTileDimensions.TILE_SMALL_PADDING
            }
        }

        return MahjongPlayerAreaLayoutResult(
            cornerYieldShift = cornerYieldShift,
            standing = standing,
            drawnSlot = drawnSlot,
            melds = melds,
            setAside = setAside,
        )
    }

    /** 這張副露牌是否蓋著擺放，依副露的逐牌描述判定，不以副露種類推測。 */
    private fun MahjongMeldTileGroup.isFaceDown(tileId: Uuid): Boolean = allTilesFaceDown || tileId in faceDownTileIds
}
