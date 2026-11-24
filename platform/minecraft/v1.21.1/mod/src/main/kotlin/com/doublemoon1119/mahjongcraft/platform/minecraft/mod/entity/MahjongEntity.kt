package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

/**
 * 本 mod 實體的共同基底，讓實體以相同的寫法宣告同步資料。
 *
 * 原版宣告同步資料的方法在不同 Minecraft 版本有不同的簽章；實體改覆寫 [defineSyncedData]，由這個類別轉接到這個版本的
 * 原版方法。用戶端收到新位置的 `lerpTo` 在這個版本直接是原版方法。
 *
 * @param type 實體類型。
 * @param world 實體所屬世界。
 */
abstract class MahjongEntity(type: EntityType<*>, world: Level) : Entity(type, world) {
    final override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        defineSyncedData(
            data = object : SyncedDataDefiner {
                override fun <T : Any> define(key: EntityDataAccessor<T>, value: T) {
                    builder.define(key, value)
                }
            },
        )
    }

    /**
     * 宣告這個實體要同步到客戶端的資料；建立實體時呼叫一次。
     *
     * @param data 宣告同步資料用的對象。
     */
    protected abstract fun defineSyncedData(data: SyncedDataDefiner)
}
