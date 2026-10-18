package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.meldDisplayDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallSideLayout
import kotlin.uuid.Uuid

/**
 * 虛擬桌的正式布局呼叫參數；不對應任何實際 controller block 或對局狀態。
 *
 * 所有格位一律轉呼叫正式的 [MahjongTileTableLayout]，debug 預覽與正式對局因此共用同一份幾何規則。
 * 由 [DebugVirtualTableLayoutFactory] 依呼叫者所在座標建立。
 *
 * @property controllerX 虛擬 controller 的方塊 X 座標。
 * @property controllerY 虛擬 controller 的方塊 Y 座標。
 * @property controllerZ 虛擬 controller 的方塊 Z 座標。
 * @property tableFacing 虛擬桌的朝向。
 * @property seatLayout 此次預覽使用的玩家座位布局。
 * @property wallSideLayout 此次預覽使用的牌牆側面布局。
 */
data class DebugVirtualTableLayout(
    val controllerX: Int,
    val controllerY: Int,
    val controllerZ: Int,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val wallSideLayout: MahjongTileWallSideLayout,
) {
    /**
     * 取得座位 0 的正式手牌格位。
     *
     * @param handSize 手牌張數。
     * @param tileIndex 手牌中的格位索引。
     * @return 指定手牌的世界格位。
     */
    fun handPlacement(handSize: Int, tileIndex: Int): MahjongTileWallPlacement = handPlacement(DEBUG_SEAT_INDEX, handSize, tileIndex)

    /**
     * 取得指定座位的正式手牌格位，供多家和 showcase 使用。
     *
     * @param seatIndex 座位索引。
     * @param handSize 手牌張數。
     * @param tileIndex 手牌中的格位索引。
     * @param cornerYieldShift 沿桌角方向額外讓出的距離。
     * @return 指定手牌的世界格位。
     */
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
        seatLayout = seatLayout,
        seatIndex = seatIndex,
        handSize = handSize,
        tileIndex = tileIndex,
        cornerYieldShift = cornerYieldShift,
    )

    /**
     * 取得與正式桌面完全一致的 showcase 世界中心。
     *
     * @return showcase 舞台中心的世界格位。
     */
    fun showcaseStagePlacement(): MahjongTileWallPlacement = MahjongTileTableLayout.showcaseStagePlacement(controllerX, controllerY, controllerZ)

    /**
     * 取得座位 0 的正式摸牌格位。
     *
     * @param standingTileCount 摸牌前手牌中的直立牌張數。
     * @return 摸牌牌張的世界格位。
     */
    fun drawnTilePlacement(standingTileCount: Int): MahjongTileWallPlacement = MahjongTileTableLayout.drawnTilePlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        seatLayout = seatLayout,
        seatIndex = DEBUG_SEAT_INDEX,
        standingTileCount = standingTileCount,
    )

    /**
     * 取得標準 17 墩牌牆第一面的正式牌張格位。
     *
     * @param tileIndex 牌牆牌張索引。
     * @return 指定牌張的世界格位。
     */
    fun wallPlacement(tileIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        sideLayout = wallSideLayout,
        stacksPerSide = STANDARD_WALL_STACKS_PER_SIDE,
        position = TileWallPosition(
            side = 0,
            stack = tileIndex / WALL_LAYERS_PER_STACK,
            layer = tileIndex % WALL_LAYERS_PER_STACK,
        ),
    )

    /**
     * 取得座位 0 第一張正式牌河格位。
     *
     * @param discardIndex 牌河中的牌張索引。
     * @return 指定牌河牌張的世界格位。
     */
    fun discardPlacement(discardIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.discardPlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        seatLayout = seatLayout,
        seatIndex = DEBUG_SEAT_INDEX,
        discardIndex = discardIndex,
        isSidewaysMarked = false,
        sidewaysMarkedDiscardIndex = null,
        wallRemaining = true,
    )

    /**
     * 依正式副露游標、鳴牌來源與牌寬規則，取得單組吃、碰或明槓的格位。
     *
     * @param type 副露類型。
     * @param tileCount 這組副露要排出的牌張數。
     * @return 由桌角向手牌方向排列的格位。
     */
    fun meldPlacements(type: MeldType, tileCount: Int): List<MahjongTileWallPlacement> {
        val sourceDirection = if (type == MeldType.CHI) RelativeDirection.Left else RelativeDirection.Across
        return singleMeld(sourceDirection, tileCount).placements
    }

    /**
     * 取得一組加槓的格位：先照碰的排法排出 [tileCount] 張，最後一個格位是加槓疊上去的第四張。
     *
     * 疊放位置與正式路徑相同：沿排列方向對齊橫置的鳴取牌，往桌子深度方向讓開一個
     * [MahjongTileTableLayout.ADDED_KAN_DEPTH_OFFSET]。
     *
     * @param tileCount 加槓疊放前的碰牌張數。
     * @return 包含碰牌排列及加槓疊放位置的格位。
     */
    fun addedKanMeldPlacements(tileCount: Int): List<MahjongTileWallPlacement> {
        val meld = singleMeld(RelativeDirection.Across, tileCount)
        val addedPlacement = MahjongTileTableLayout.meldPlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            seatLayout = seatLayout,
            seatIndex = DEBUG_SEAT_INDEX,
            alongOffsetFromCorner = meld.sidewaysAlongOffset,
            isSidewaysTile = true,
            depthOffsetFromEdge = MahjongTileTableLayout.ADDED_KAN_DEPTH_OFFSET,
        )
        return meld.placements + addedPlacement
    }

    /**
     * 依正式副露游標排出單一組副露，並記下橫置鳴取牌的游標位移供加槓疊放使用。
     *
     * @param sourceDirection 鳴取牌來源方向。
     * @param tileCount 副露牌張數。
     * @return 每張牌的格位與橫置牌沿排列方向的位移。
     */
    private fun singleMeld(sourceDirection: RelativeDirection, tileCount: Int): SingleMeldPlacements {
        val sidewaysSlot = MahjongTileTableLayout.sidewaysSlotIndex(
            meldDisplayDirection(sourceDirection, seatLayout.playerCount),
            tileCount,
        )
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
                seatLayout = seatLayout,
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

    /**
     * 依正式副露游標規則取得指定座位多組副露中每張牌的格位。
     *
     * 加槓的最後一張是補上的牌，疊在橫置鳴取牌靠桌子中心的一側，不佔用排列方向的格位。
     *
     * @param melds 依鳴牌順序的副露。
     * @param seatIndex 副露所屬的座位。
     * @return 以牌張識別碼索引的世界格位。
     */
    fun meldPlacements(
        melds: List<MahjongMeldTileGroup>,
        seatIndex: Int = DEBUG_SEAT_INDEX,
    ): Map<Uuid, MahjongTileWallPlacement> {
        val placements = mutableMapOf<Uuid, MahjongTileWallPlacement>()
        var cursorAlong = 0.0
        melds.forEachIndexed { meldIndex, meld ->
            if (meldIndex > 0) cursorAlong += MahjongTileTableLayout.MELD_GROUP_GAP
            val isAddedKan = meld.type == MeldType.ADDED_KAN
            val baseTileIds = if (isAddedKan) meld.tileIds.dropLast(1) else meld.tileIds
            val displayDirection = meldDisplayDirection(meld.sourceDirection, seatLayout.playerCount)
            val sidewaysSlot = meld.calledTileId?.let {
                MahjongTileTableLayout.sidewaysSlotIndex(displayDirection, baseTileIds.size)
            }
            val remainingTileIds = ArrayDeque(baseTileIds.filterNot { it == meld.calledTileId })
            val tileAtSlot = baseTileIds.indices.map { slot ->
                if (slot == sidewaysSlot) meld.calledTileId!! else remainingTileIds.removeFirst()
            }
            var sidewaysAlongOffset = 0.0
            for (slot in tileAtSlot.indices.reversed()) {
                val isSideways = slot == sidewaysSlot
                val halfWidth =
                    if (isSideways) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_WIDTH / 2.0
                cursorAlong += halfWidth
                if (isSideways) sidewaysAlongOffset = cursorAlong
                placements[tileAtSlot[slot]] = MahjongTileTableLayout.meldPlacement(
                    controllerX = controllerX,
                    controllerY = controllerY,
                    controllerZ = controllerZ,
                    tableFacing = tableFacing,
                    seatLayout = seatLayout,
                    seatIndex = seatIndex,
                    alongOffsetFromCorner = cursorAlong,
                    isSidewaysTile = isSideways,
                )
                cursorAlong += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
            }
            if (isAddedKan) {
                placements[meld.tileIds.last()] = MahjongTileTableLayout.meldPlacement(
                    controllerX = controllerX,
                    controllerY = controllerY,
                    controllerZ = controllerZ,
                    tableFacing = tableFacing,
                    seatLayout = seatLayout,
                    seatIndex = seatIndex,
                    alongOffsetFromCorner = sidewaysAlongOffset,
                    isSidewaysTile = true,
                    depthOffsetFromEdge = MahjongTileTableLayout.ADDED_KAN_DEPTH_OFFSET,
                )
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
