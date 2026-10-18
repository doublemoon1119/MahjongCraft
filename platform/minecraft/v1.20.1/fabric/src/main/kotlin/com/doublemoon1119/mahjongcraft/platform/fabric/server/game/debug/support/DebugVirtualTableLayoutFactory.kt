package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallSideLayout

/**
 * 依呼叫者所在的方塊座標建立 debug 預覽用的虛擬桌布局。
 *
 * 無狀態，也不接觸世界或 entity：只把虛擬 controller 沿玩家視線前推固定距離，讓座位 0 的正式布局落在
 * 玩家面前，再交由 [DebugVirtualTableLayout] 轉呼叫正式幾何。
 */
class DebugVirtualTableLayoutFactory {
    /**
     * 以玩家腳下方塊為基準建立虛擬桌布局。
     *
     * @param playerBlockX 玩家所在方塊的 X 座標。
     * @param playerBlockY 玩家所在方塊的 Y 座標。
     * @param playerBlockZ 玩家所在方塊的 Z 座標。
     * @param tableFacing 虛擬桌朝向。
     * @param seatLayout 預覽使用的玩家座位布局。
     * @return 相對玩家座標建立的虛擬桌布局。
     */
    fun create(
        playerBlockX: Int,
        playerBlockY: Int,
        playerBlockZ: Int,
        tableFacing: MahjongTableFacing,
        seatLayout: MahjongTableSeatLayout,
    ): DebugVirtualTableLayout {
        val (offsetX, offsetZ) = when (tableFacing) {
            MahjongTableFacing.NORTH -> 0 to -VIRTUAL_CONTROLLER_FORWARD_BLOCKS
            MahjongTableFacing.EAST -> VIRTUAL_CONTROLLER_FORWARD_BLOCKS to 0
            MahjongTableFacing.SOUTH -> 0 to VIRTUAL_CONTROLLER_FORWARD_BLOCKS
            MahjongTableFacing.WEST -> -VIRTUAL_CONTROLLER_FORWARD_BLOCKS to 0
        }
        return DebugVirtualTableLayout(
            controllerX = playerBlockX + offsetX,
            controllerY = playerBlockY,
            controllerZ = playerBlockZ + offsetZ,
            tableFacing = tableFacing,
            seatLayout = seatLayout,
            wallSideLayout = STANDARD_WALL_SIDE_LAYOUT,
        )
    }

    companion object {
        /** Debug 預覽固定使用四面牌牆，與四人日麻的標準側面順序一致。 */
        val STANDARD_WALL_SIDE_LAYOUT: MahjongTileWallSideLayout = MahjongTileWallSideLayout.resolve(
            arrangement = TileWallSideArrangement(
                reference = TileWallSideReference.TABLE,
                quarterTurns = listOf(0, 3, 2, 1),
            ),
            dealerSide = MahjongTableSide.SOUTH,
        )

        /** 虛擬 controller 與玩家腳下方塊的水平距離，使近側手牌落在玩家前方約一格處。 */
        const val VIRTUAL_CONTROLLER_FORWARD_BLOCKS: Int = 3
    }
}
