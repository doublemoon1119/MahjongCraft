package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import kotlin.uuid.Uuid

/**
 * 虛擬桌的正式布局呼叫參數；不對應任何實際 controller block 或對局狀態。
 *
 * 所有格位一律轉呼叫正式的 [MahjongTileTableLayout]，debug 預覽與正式對局因此共用同一份幾何規則。
 * 由 [DebugVirtualTableLayoutFactory] 依呼叫者所在座標建立。
 */
data class DebugVirtualTableLayout(
    /** 虛擬 controller 的方塊 X 座標。 */
    val controllerX: Int,
    /** 虛擬 controller 的方塊 Y 座標。 */
    val controllerY: Int,
    /** 虛擬 controller 的方塊 Z 座標。 */
    val controllerZ: Int,
    /** 虛擬桌的朝向。 */
    val tableFacing: MahjongTableFacing,
) {
    /** 取得座位 0 的正式手牌格位。 */
    fun handPlacement(handSize: Int, tileIndex: Int): MahjongTileWallPlacement = handPlacement(DEBUG_SEAT_INDEX, handSize, tileIndex)

    /** 取得指定座位的正式手牌格位，供多家和 showcase 使用。 */
    fun handPlacement(
        seatIndex: Int,
        handSize: Int,
        tileIndex: Int,
        cornerYieldShift: Double = 0.0,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.handPlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        seatIndex = seatIndex,
        handSize = handSize,
        tileIndex = tileIndex,
        cornerYieldShift = cornerYieldShift,
    )

    /** 取得與正式桌面完全一致的 showcase 世界中心。 */
    fun showcaseStagePlacement(): MahjongTileWallPlacement = MahjongTileTableLayout.showcaseStagePlacement(controllerX, controllerY, controllerZ)

    /** 取得座位 0 的正式摸牌格位。 */
    fun drawnTilePlacement(standingTileCount: Int): MahjongTileWallPlacement = MahjongTileTableLayout.drawnTilePlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        seatIndex = DEBUG_SEAT_INDEX,
        standingTileCount = standingTileCount,
    )

    /** 取得標準 17 墩牌牆第一面的正式牌張格位。 */
    fun wallPlacement(tileIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        dealerSeatIndex = DEBUG_SEAT_INDEX,
        stacksPerSide = STANDARD_WALL_STACKS_PER_SIDE,
        position = TileWallPosition(
            side = 0,
            stack = tileIndex / WALL_LAYERS_PER_STACK,
            layer = tileIndex % WALL_LAYERS_PER_STACK,
        ),
    )

    /** 取得座位 0 第一張正式牌河格位。 */
    fun discardPlacement(discardIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.discardPlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        seatIndex = DEBUG_SEAT_INDEX,
        discardIndex = discardIndex,
        isSidewaysMarked = false,
        sidewaysMarkedDiscardIndex = null,
        wallRemaining = true,
    )

    /** 依正式副露游標、鳴牌來源與牌寬規則，取得單組吃、碰或明槓的格位。 */
    fun meldPlacements(type: MeldType, tileCount: Int): List<MahjongTileWallPlacement> {
        val sourceDirection = if (type == MeldType.CHI) RelativeDirection.Left else RelativeDirection.Across
        return singleMeld(sourceDirection, tileCount).placements
    }

    /**
     * 取得一組加槓的格位：先照碰的排法排出 [tileCount] 張，最後一個格位是加槓疊上去的第四張。
     *
     * 疊放位置與正式路徑相同：沿排列方向對齊橫置的鳴取牌，往桌子深度方向讓開一個
     * [MahjongTileTableLayout.ADDED_KAN_DEPTH_OFFSET]。
     */
    fun addedKanMeldPlacements(tileCount: Int): List<MahjongTileWallPlacement> {
        val meld = singleMeld(RelativeDirection.Across, tileCount)
        val addedPlacement = MahjongTileTableLayout.meldPlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            seatIndex = DEBUG_SEAT_INDEX,
            alongOffsetFromCorner = meld.sidewaysAlongOffset,
            isSidewaysTile = true,
            depthOffsetFromEdge = MahjongTileTableLayout.ADDED_KAN_DEPTH_OFFSET,
        )
        return meld.placements + addedPlacement
    }

    /** 依正式副露游標排出單一組副露，並記下橫置鳴取牌的游標位移供加槓疊放使用。 */
    private fun singleMeld(sourceDirection: RelativeDirection, tileCount: Int): SingleMeldPlacements {
        val sidewaysSlot = MahjongTileTableLayout.sidewaysSlotIndex(sourceDirection, tileCount)
        var cursorAlong = 0.0
        var sidewaysAlongOffset = 0.0
        val placements = (tileCount - 1 downTo 0).map { slot ->
            val isSideways = slot == sidewaysSlot
            val halfWidth = if (isSideways) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_WIDTH / 2.0
            cursorAlong += halfWidth
            if (isSideways) sidewaysAlongOffset = cursorAlong
            val placement = MahjongTileTableLayout.meldPlacement(
                controllerX = controllerX,
                controllerY = controllerY,
                controllerZ = controllerZ,
                tableFacing = tableFacing,
                seatIndex = DEBUG_SEAT_INDEX,
                alongOffsetFromCorner = cursorAlong,
                isSidewaysTile = isSideways,
            )
            cursorAlong += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
            placement
        }.reversed()
        return SingleMeldPlacements(placements, sidewaysAlongOffset)
    }

    /**
     * 單一組副露的格位與橫置鳴取牌的游標位移。
     *
     * @property placements 這組副露由桌角往手牌方向的格位。
     * @property sidewaysAlongOffset 橫置鳴取牌沿排列方向距離桌角的位移；沒有橫置牌時為 `0.0`。
     */
    private data class SingleMeldPlacements(
        val placements: List<MahjongTileWallPlacement>,
        val sidewaysAlongOffset: Double,
    )

    /** 依正式副露游標規則取得多組副露中每張牌的格位。 */
    fun meldPlacements(melds: List<MahjongMeldTileGroup>): Map<Uuid, MahjongTileWallPlacement> {
        val placements = mutableMapOf<Uuid, MahjongTileWallPlacement>()
        var cursorAlong = 0.0
        melds.forEachIndexed { meldIndex, meld ->
            if (meldIndex > 0) cursorAlong += MahjongTileTableLayout.MELD_GROUP_GAP
            val sidewaysSlot = meld.calledTileId?.let {
                MahjongTileTableLayout.sidewaysSlotIndex(meld.sourceDirection, meld.tileIds.size)
            }
            val remainingTileIds = ArrayDeque(meld.tileIds.filterNot { it == meld.calledTileId })
            val tileAtSlot = meld.tileIds.indices.map { slot ->
                if (slot == sidewaysSlot) meld.calledTileId!! else remainingTileIds.removeFirst()
            }
            for (slot in tileAtSlot.indices.reversed()) {
                val isSideways = slot == sidewaysSlot
                val halfWidth =
                    if (isSideways) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_WIDTH / 2.0
                cursorAlong += halfWidth
                placements[tileAtSlot[slot]] = MahjongTileTableLayout.meldPlacement(
                    controllerX = controllerX,
                    controllerY = controllerY,
                    controllerZ = controllerZ,
                    tableFacing = tableFacing,
                    seatIndex = DEBUG_SEAT_INDEX,
                    alongOffsetFromCorner = cursorAlong,
                    isSidewaysTile = isSideways,
                )
                cursorAlong += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
            }
        }
        return placements
    }

    companion object {
        /** 預覽固定使用座位 0，對應虛擬桌面向玩家的近側。 */
        const val DEBUG_SEAT_INDEX: Int = 0

        /** 標準四人日麻共 136 張牌，四面各 17 墩。 */
        const val STANDARD_WALL_STACKS_PER_SIDE: Int = 17

        /** 每墩牌牆固定上下兩層。 */
        const val WALL_LAYERS_PER_STACK: Int = 2
    }
}
