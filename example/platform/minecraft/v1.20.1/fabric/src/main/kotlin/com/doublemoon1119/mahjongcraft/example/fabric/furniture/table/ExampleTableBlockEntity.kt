package com.doublemoon1119.mahjongcraft.example.fabric.furniture.table

import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.readNbt
import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.writeNbt
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableController
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableControllerState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/**
 * 三種範例麻將桌共用的控制方塊實體。
 *
 * MahjongCraft 只依 [MahjongTableController] 辨識桌子，不需要登記；狀態物件交給 MahjongCraft 的 NBT 函式存讀。
 *
 * @param pos 方塊實體所在位置。
 * @param state 建立時的方塊狀態。
 */
class ExampleTableBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(ExampleTables.blockEntityType, pos, state),
    MahjongTableController {
    /** MahjongCraft 的桌子狀態，隨此方塊實體存讀檔。 */
    override val mahjongTableState: MahjongTableControllerState = MahjongTableControllerState(::markDirty)

    /** 由控制方塊狀態的水平朝向轉成 MahjongCraft 的朝向。 */
    override val mahjongTableFacing: MahjongTableFacing
        get() = when (cachedState.get(Properties.HORIZONTAL_FACING)) {
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
    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        mahjongTableState.readNbt(nbt)
    }

    /**
     * 存檔時寫入麻將桌狀態。
     *
     * @param nbt 方塊實體的 NBT。
     */
    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        mahjongTableState.writeNbt(nbt)
    }
}
