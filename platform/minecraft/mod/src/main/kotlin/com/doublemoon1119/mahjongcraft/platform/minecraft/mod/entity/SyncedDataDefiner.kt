package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import net.minecraft.network.syncher.EntityDataAccessor

/** 在 [MahjongEntity.defineSyncedData] 中宣告實體要同步到客戶端的資料。 */
interface SyncedDataDefiner {
    /**
     * 宣告一項同步資料與它的初始值。
     *
     * @param T 資料的型別，不可為 null。
     * @param key 資料的識別。
     * @param value 初始值。
     */
    fun <T : Any> define(key: EntityDataAccessor<T>, value: T)
}
