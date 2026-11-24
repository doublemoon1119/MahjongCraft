package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

/**
 * 本 mod 實體的共同基底，讓實體以相同的寫法宣告同步資料。
 *
 * 原版宣告同步資料的方法在不同 Minecraft 版本有不同的簽章；實體改覆寫 [defineSyncedData]，由這個類別轉接到這個版本的
 * 原版方法。用戶端收到新位置的 [lerpTo] 以新版 Minecraft 的參數撰寫，同樣由這個類別轉接。
 *
 * @param type 實體類型。
 * @param world 實體所屬世界。
 */
abstract class MahjongEntity(type: EntityType<*>, world: Level) : Entity(type, world) {
    final override fun defineSynchedData() {
        defineSyncedData(
            data = object : SyncedDataDefiner {
                override fun <T : Any> define(key: EntityDataAccessor<T>, value: T) = entityData.define(key, value)
            },
        )
    }

    /**
     * 用戶端收到伺服器送來的新位置與角度時呼叫，參數與新版 Minecraft 相同。
     *
     * @param x 新位置 X 座標。
     * @param y 新位置 Y 座標。
     * @param z 新位置 Z 座標。
     * @param yRot 新的水平角度。
     * @param xRot 新的垂直角度。
     * @param steps 補間到新位置的 tick 數。
     */
    open fun lerpTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float, steps: Int) {
        super.lerpTo(x, y, z, yRot, xRot, steps, false)
    }

    final override fun lerpTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float, steps: Int, teleport: Boolean) {
        lerpTo(x, y, z, yRot, xRot, steps)
    }

    /**
     * 宣告這個實體要同步到客戶端的資料；建立實體時呼叫一次。
     *
     * @param data 宣告同步資料用的對象。
     */
    protected abstract fun defineSyncedData(data: SyncedDataDefiner)
}
