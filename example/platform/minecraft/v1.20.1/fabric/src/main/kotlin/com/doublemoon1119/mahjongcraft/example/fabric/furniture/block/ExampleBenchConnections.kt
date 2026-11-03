package com.doublemoon1119.mahjongcraft.example.fabric.furniture.block

import net.minecraft.util.math.Direction

/** 長凳只沿自身左右方向連接同朝向的相鄰長凳。 */
internal object ExampleBenchConnections {
    /**
     * 判斷同高度鄰格是否連接，不檢查更遠的方塊。
     *
     * @param facing 本格朝向。
     * @param side 鄰格相對本格的方向。
     * @param neighborFacing 已載入鄰格的長凳朝向；不是長凳或未載入時為 null。
     * @return 鄰格位於左右且同朝向時為 true。
     */
    fun connects(
        facing: Direction,
        side: Direction,
        neighborFacing: Direction?,
    ): Boolean = facing.axis.isHorizontal &&
        neighborFacing == facing &&
        (side == facing.rotateYClockwise() || side == facing.rotateYCounterclockwise())
}
