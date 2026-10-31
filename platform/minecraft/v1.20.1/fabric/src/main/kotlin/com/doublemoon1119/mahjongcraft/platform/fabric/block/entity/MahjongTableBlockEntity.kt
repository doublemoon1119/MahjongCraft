package com.doublemoon1119.mahjongcraft.platform.fabric.block.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.readNbt
import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.writeNbt
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableController
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableControllerState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

/**
 * 內建麻將桌的控制方塊實體，位於結構底層中央。
 *
 * @param pos 此方塊實體所在位置。
 * @param state 建立時的方塊狀態。
 */
class MahjongTableBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(ModBlocks.mahjongTableBlockEntity, pos, state),
    MahjongTableController {
    /** 桌子 ID 與呈現時間軸，隨此方塊實體存讀檔。 */
    override val mahjongTableState: MahjongTableControllerState = MahjongTableControllerState(::markDirty)

    /** 由控制方塊狀態的水平朝向決定。 */
    override val mahjongTableFacing: MahjongTableFacing
        get() = cachedState.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()

    /**
     * 從方塊實體 NBT 還原麻將桌狀態。
     *
     * @param nbt 要讀取的 NBT 欄位。
     */
    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        mahjongTableState.readNbt(nbt)
    }

    /**
     * 把麻將桌狀態寫入方塊實體 NBT。
     *
     * @param nbt 接收序列化欄位的 NBT。
     */
    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        mahjongTableState.writeNbt(nbt)
    }
}
