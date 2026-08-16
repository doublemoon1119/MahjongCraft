package com.doublemoon1119.mahjongcraft.platform.fabric.server.dice

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import net.minecraft.util.math.Direction

/** 將 Fabric 水平朝向轉為共用麻將桌 layout 朝向。 */
fun Direction.toMahjongTableFacing(): MahjongTableFacing = when (this) {
    Direction.NORTH -> MahjongTableFacing.NORTH
    Direction.EAST -> MahjongTableFacing.EAST
    Direction.SOUTH -> MahjongTableFacing.SOUTH
    Direction.WEST -> MahjongTableFacing.WEST
    else -> error("Mahjong table facing must be horizontal")
}
