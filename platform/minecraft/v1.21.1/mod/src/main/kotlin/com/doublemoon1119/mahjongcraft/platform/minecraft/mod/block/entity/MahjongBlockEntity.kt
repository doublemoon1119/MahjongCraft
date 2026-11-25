package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * 本 mod 方塊實體的基底：子類別在 [readData] 與 [writeData] 存讀自己的資料，各版本的原版存讀方法都交給它們。
 *
 * @param type 方塊實體類型。
 * @param pos 方塊實體所在位置。
 * @param state 建立時的方塊狀態。
 */
abstract class MahjongBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(type, pos, state) {
    /**
     * 從方塊實體 NBT 還原自己的資料。
     *
     * @param nbt 要讀取的 NBT。
     */
    protected abstract fun readData(nbt: CompoundTag)

    /**
     * 把自己的資料寫入方塊實體 NBT。
     *
     * @param nbt 接收資料的 NBT。
     */
    protected abstract fun writeData(nbt: CompoundTag)

    final override fun loadAdditional(nbt: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(nbt, registries)
        readData(nbt)
    }

    final override fun saveAdditional(nbt: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(nbt, registries)
        writeData(nbt)
    }
}
