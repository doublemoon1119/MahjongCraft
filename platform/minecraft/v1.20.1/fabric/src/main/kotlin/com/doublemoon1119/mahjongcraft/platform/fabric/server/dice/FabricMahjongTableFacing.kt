package com.doublemoon1119.mahjongcraft.platform.fabric.server.dice

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import net.minecraft.util.math.Direction

/**
 * 將 Minecraft 水平朝向轉為共用麻將桌配置朝向。
 *
 * @return 相同方向的共用朝向值。
 * @throws IllegalStateException 接收的朝向為上或下，並非水平方向。
 */
fun Direction.toMahjongTableFacing(): MahjongTableFacing = when (this) {
    Direction.NORTH -> MahjongTableFacing.NORTH
    Direction.EAST -> MahjongTableFacing.EAST
    Direction.SOUTH -> MahjongTableFacing.SOUTH
    Direction.WEST -> MahjongTableFacing.WEST
    else -> error("Mahjong table facing must be horizontal")
}
