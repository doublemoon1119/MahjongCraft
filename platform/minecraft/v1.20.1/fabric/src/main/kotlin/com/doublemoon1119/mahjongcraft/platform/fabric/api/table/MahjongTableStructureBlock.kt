package com.doublemoon1119.mahjongcraft.platform.fabric.api.table

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView

/**
 * 組成麻將桌的方塊，由方塊實作。
 *
 * MahjongCraft 以此判斷某個方塊屬於哪一張桌子，例如拆除任何一格時依同一張桌子的狀態決定是否允許。
 * 控制方塊本身也實作此介面並回傳自己的位置。
 */
interface MahjongTableStructureBlock {
    /**
     * 回傳此方塊所屬桌子的控制方塊位置。
     *
     * @param world 方塊所在的世界。
     * @param pos 此方塊的位置。
     * @param state 此方塊目前的狀態。
     * @return 控制方塊位置；此方塊目前不屬於任何桌子時為 null。
     */
    fun mahjongTableControllerPos(
        world: BlockView,
        pos: BlockPos,
        state: BlockState,
    ): BlockPos?
}
