package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.PublicHandOrder
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
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

/**
 * 計算一位玩家牌區需要的資料。
 *
 * @property controllerX 麻將桌 controller 的世界 X 座標。
 * @property controllerY 麻將桌 controller 的世界 Y 座標。
 * @property controllerZ 麻將桌 controller 的世界 Z 座標。
 * @property tableFacing 麻將桌的世界朝向。
 * @property seatLayout 本局玩家座位 index 對應的標準桌面布局。
 * @property seatIndex 玩家在固定座位順序中的 index。
 * @property standingTileIds 立牌，不含摸牌位的牌。
 * @property hasDrawnTile 摸牌位上是否有牌。
 * @property melds 依宣告順序排列的副露。
 * @property setAsideTileIds 依移出順序排列、接在副露之後的移出牌。
 * @property cornerWidth 桌角物件佔用的寬度。
 */
data class MahjongPlayerAreaLayoutInput(
    val controllerX: Int,
    val controllerY: Int,
    val controllerZ: Int,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val seatIndex: Int,
    val standingTileIds: List<Uuid>,
    val hasDrawnTile: Boolean,
    val melds: List<MahjongMeldTileGroup>,
    val setAsideTileIds: List<Uuid>,
    val cornerWidth: Double,
)

/**
 * 一位玩家牌區每張牌的最終位置。
 *
 * @property cornerYieldShift 立牌與摸牌位為了避開桌角區一起往玩家方向平移的距離。
 * @property standing 依公開順序排列的立牌。
 * @property drawnSlot 摸牌位。
 * @property melds 依宣告順序排列的副露，每組依擺放順序列出每張牌；加槓補上的牌排在該組最後。
 * @property setAside 依移出順序排列的移出牌。
 */
data class MahjongPlayerAreaLayoutResult(
    val cornerYieldShift: Double,
    val standing: List<MahjongAreaTilePlacement>,
    val drawnSlot: MahjongTileWallPlacement,
    val melds: List<List<MahjongAreaTilePlacement>>,
    val setAside: List<MahjongAreaTilePlacement>,
)

/**
 * 牌區中一張牌的最終位置。
 *
 * @property tileId 牌的識別碼。
 * @property placement 世界座標與朝向。
 * @property footprint 這張牌在桌面上佔用的形狀。
 * @property alongOffsetFromCorner 副露與移出的牌中心沿排列方向距離桌角錨點的距離；立牌為 null。
 * @property isFaceDown 是否蓋著擺放。
 */
data class MahjongAreaTilePlacement(
    val tileId: Uuid,
    val placement: MahjongTileWallPlacement,
    val footprint: MahjongTileFootprint,
    val alongOffsetFromCorner: Double?,
    val isFaceDown: Boolean = false,
)

/** 一張牌在桌面上佔用的形狀。 */
enum class MahjongTileFootprint {
    /** 直立：沿排列方向為牌寬，垂直方向為牌厚。 */
    STANDING,

    /** 平放：沿排列方向為牌寬，垂直方向為牌高。 */
    LYING,

    /** 橫放：沿排列方向為牌高，垂直方向為牌寬。 */
    LYING_SIDEWAYS,
}
