package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing

/**
 * 將抽象牌牆 placement 投影到特定桌子的必要資料。
 *
 * @property controllerX 控制方塊 X 座標。
 * @property controllerY 控制方塊 Y 座標。
 * @property controllerZ 控制方塊 Z 座標。
 * @property tableFacing 桌子在世界中的朝向。
 * @property dealerSeatIndex 本局莊家座位索引。
 * @property stacksPerSide 牌牆每面的墩數。
 * @property sideCount 牌牆的面數，與玩家人數相同。
 */
data class MahjongTileWallProjectionContext(
    val controllerX: Int,
    val controllerY: Int,
    val controllerZ: Int,
    val tableFacing: MahjongTableFacing,
    val dealerSeatIndex: Int,
    val stacksPerSide: Int,
    val sideCount: Int,
) {
    init {
        require(stacksPerSide > 0) { "Stacks per side must be positive" }
        require(sideCount > 0) { "Wall side count must be positive" }
    }

    /** 將 [placement] 投影成此桌的世界座標與朝向。 */
    fun project(placement: TileWallPlacement): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        dealerSeatIndex = dealerSeatIndex,
        stacksPerSide = stacksPerSide,
        sideCount = sideCount,
        placement = placement,
    )
}
