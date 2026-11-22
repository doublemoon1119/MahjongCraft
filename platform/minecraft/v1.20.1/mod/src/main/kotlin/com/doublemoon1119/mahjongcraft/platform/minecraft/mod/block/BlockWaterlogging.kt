package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids

/**
 * 依放置位置原本是否為水源，決定方塊是否含水，與原版可含水方塊相同。
 *
 * @param world 放置的世界。
 * @param pos 放置位置。
 * @return 依原位置設定含水狀態的方塊狀態。
 */
internal fun BlockState.waterloggedAt(world: BlockGetter, pos: BlockPos): BlockState = setValue(BlockStateProperties.WATERLOGGED, world.getFluidState(pos).type == Fluids.WATER)

/** @return 含水時為靜止水源，否則為空的流體狀態。 */
internal fun BlockState.waterloggedFluidState(): FluidState = if (getValue(BlockStateProperties.WATERLOGGED)) Fluids.WATER.getSource(false) else Fluids.EMPTY.defaultFluidState()

/**
 * 含水時排程水流更新，讓周圍的水照常流動。
 *
 * @param world 方塊所在世界。
 * @param pos 方塊位置。
 */
internal fun BlockState.scheduleWaterTickIfWaterlogged(world: LevelAccessor, pos: BlockPos) {
    if (getValue(BlockStateProperties.WATERLOGGED)) world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
}
