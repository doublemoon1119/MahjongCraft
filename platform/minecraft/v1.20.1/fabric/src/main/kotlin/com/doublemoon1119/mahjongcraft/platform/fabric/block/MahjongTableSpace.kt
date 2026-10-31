package com.doublemoon1119.mahjongcraft.platform.fabric.block

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSpace
import net.minecraft.util.math.BlockPos

/**
 * 列出擺牌那一層的方塊位置。
 *
 * @param controllerPos 控制方塊位置。
 * @return 控制方塊上方一格、以其為中心的 3×3 位置。
 */
internal fun MahjongTableSpace.playSpaceCells(controllerPos: BlockPos): List<BlockPos> = playSpaceHorizontalOffsets.map { (dx, dz) ->
    controllerPos.add(dx, PLAY_SPACE_OFFSET_Y, dz)
}
