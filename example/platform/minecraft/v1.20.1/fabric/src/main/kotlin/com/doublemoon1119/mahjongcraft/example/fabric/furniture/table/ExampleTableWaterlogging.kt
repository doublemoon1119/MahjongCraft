package com.doublemoon1119.mahjongcraft.example.fabric.furniture.table

import net.minecraft.block.BlockState
import net.minecraft.fluid.FluidState
import net.minecraft.fluid.Fluids
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import net.minecraft.world.WorldAccess

/**
 * 依位置原本是否為水源設定含水狀態，與原版可含水方塊相同。
 *
 * @param world 放置的世界。
 * @param pos 放置位置。
 * @return 依原位置設定含水狀態的方塊狀態。
 */
internal fun BlockState.waterloggedAt(world: BlockView, pos: BlockPos): BlockState = with(Properties.WATERLOGGED, world.getFluidState(pos).fluid == Fluids.WATER)

/** @return 含水時為靜止水源，否則為空的流體狀態。 */
internal fun BlockState.waterloggedFluidState(): FluidState = if (get(Properties.WATERLOGGED)) Fluids.WATER.getStill(false) else Fluids.EMPTY.defaultState

/**
 * 含水時排程水流更新，讓周圍的水照常流動。
 *
 * @param world 方塊所在世界。
 * @param pos 方塊位置。
 */
internal fun BlockState.scheduleWaterTickIfWaterlogged(world: WorldAccess, pos: BlockPos) {
    if (get(Properties.WATERLOGGED)) world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world))
}
