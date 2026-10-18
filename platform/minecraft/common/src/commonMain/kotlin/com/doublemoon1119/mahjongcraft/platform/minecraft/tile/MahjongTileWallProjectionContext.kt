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
 * @property sideLayout 規則指定並依本局莊家解析的牌牆面與桌邊對應。
 * @property stacksPerSide 牌牆每面的墩數。
 */
data class MahjongTileWallProjectionContext(
    val controllerX: Int,
    val controllerY: Int,
    val controllerZ: Int,
    val tableFacing: MahjongTableFacing,
    val sideLayout: MahjongTileWallSideLayout,
    val stacksPerSide: Int,
) {
    /** 牌牆面數只由規則的側面對應決定。 */
    val sideCount: Int get() = sideLayout.sideCount

    init {
        require(stacksPerSide > 0) { "Stacks per side must be positive" }
    }

    /**
     * 將 [placement] 投影成此桌的世界座標與朝向。
     *
     * @param placement 抽象牌牆格位。
     * @return 此桌中的世界座標與朝向。
     */
    fun project(placement: TileWallPlacement): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        sideLayout = sideLayout,
        stacksPerSide = stacksPerSide,
        placement = placement,
    )
}
