package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.ExampleBlockEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableController
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableControllerState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.table.readNbt
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.table.writeNbt
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

/**
 * 三種範例麻將桌共用的控制方塊實體。
 *
 * MahjongCraft 只依 [MahjongTableController] 辨識桌子，不需要登記。狀態物件交給 MahjongCraft 的 NBT 函式存讀。
 *
 * @param pos 方塊實體所在位置。
 * @param state 建立時的方塊狀態。
 */
class ExampleTableBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : ExampleBlockEntity(ExampleTables.blockEntityType, pos, state),
    MahjongTableController {
    /** MahjongCraft 的桌子狀態，隨此方塊實體存讀檔。 */
    override val mahjongTableState: MahjongTableControllerState = MahjongTableControllerState(::setChanged)

    /** 由控制方塊狀態的水平朝向轉成 MahjongCraft 的朝向。 */
    override val mahjongTableFacing: MahjongTableFacing
        get() = when (blockState.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction.EAST -> MahjongTableFacing.EAST
            Direction.SOUTH -> MahjongTableFacing.SOUTH
            Direction.WEST -> MahjongTableFacing.WEST
            else -> MahjongTableFacing.NORTH
        }

    /**
     * 讀檔時還原麻將桌狀態。
     *
     * @param nbt 方塊實體的 NBT。
     */
    override fun readData(nbt: CompoundTag) {
        mahjongTableState.readNbt(nbt)
    }

    /**
     * 存檔時寫入麻將桌狀態。
     *
     * @param nbt 方塊實體的 NBT。
     */
    override fun writeData(nbt: CompoundTag) {
        mahjongTableState.writeNbt(nbt)
    }
}
