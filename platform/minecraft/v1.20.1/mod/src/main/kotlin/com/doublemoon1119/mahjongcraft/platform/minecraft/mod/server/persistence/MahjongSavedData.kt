package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData

/**
 * 本 mod 存在世界中的資料的基底：子類別在 [writeData] 寫出要保存的內容，各版本的原版儲存方法都交給它。
 */
abstract class MahjongSavedData : SavedData() {
    /**
     * 把要保存的內容寫進世界提供的容器。
     *
     * @param nbt 世界提供的保存容器。
     * @return 寫入後的容器。
     */
    abstract fun writeData(nbt: CompoundTag): CompoundTag

    final override fun save(nbt: CompoundTag): CompoundTag = writeData(nbt)
}
