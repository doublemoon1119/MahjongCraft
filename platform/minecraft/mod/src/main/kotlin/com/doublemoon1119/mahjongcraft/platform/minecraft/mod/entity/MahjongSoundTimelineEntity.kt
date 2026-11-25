package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModEntities
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import kotlin.uuid.Uuid

/**
 * 從指定世界座標播放可持久化聲音時間線、播放完畢後自行移除的無形 entity。
 *
 * @param type Minecraft entity 類型。
 * @param world 實體所屬世界。
 */
class MahjongSoundTimelineEntity(
    type: EntityType<out MahjongSoundTimelineEntity> = ModEntities.mahjongSoundTimeline,
    world: Level,
) : SimpleAnimatedMahjongEntity(type, world),
    TableOwnedPresentationEntity {
    /** 所屬麻將桌。 */
    override val managedTableId: Uuid?
        get() = entityData[TABLE_ID].takeIf(String::isNotBlank)?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }

    init {
        setNoGravity(true)
        isInvisible = true
    }

    /**
     * 設定唯一一筆立即播放、短暫有效的聲音。
     *
     * @param tableId 所屬麻將桌 UUID。
     * @param soundId 聲音 registry ID。
     * @param volume 播放音量。
     * @param pitch 播放音高。
     * @param playAtGameTime 預定播放的絕對 server game time。
     */
    fun configure(tableId: Uuid, soundId: String, volume: Float, pitch: Float, playAtGameTime: Long) {
        check(!level().isClientSide) { "Sound timeline must be configured by the server" }
        entityData.set(TABLE_ID, tableId.toString())
        enqueue(
            step = AnimationStep.PlaySound(
                soundId = soundId,
                volume = volume,
                pitch = pitch,
                playAtGameTime = playAtGameTime,
                expiresAtGameTime = playAtGameTime + SOUND_GRACE_TICKS,
            ),
        )
    }

    override fun tick() {
        super.tick()
        if (!level().isClientSide && !isAnimating) discard()
    }

    override fun defineSyncedData(data: SyncedDataDefiner) {
        data.define(TABLE_ID, "")
    }

    /**
     * 從世界存檔還原所屬桌子與待播放聲音。
     *
     * @param nbt entity 的持久化資料。
     */
    override fun readAdditionalSaveData(nbt: CompoundTag) {
        entityData.set(TABLE_ID, ManagedTableNbt.read(nbt)?.toString().orEmpty())
        readAnimationQueueFromNbt(nbt)
    }

    /**
     * 將所屬桌子與待播放聲音寫入世界存檔。
     *
     * @param nbt 接收 entity 持久化資料的 NBT。
     */
    override fun addAdditionalSaveData(nbt: CompoundTag) {
        ManagedTableNbt.write(nbt, managedTableId)
        writeAnimationQueueToNbt(nbt)
    }

    /** 固定參數。 */
    companion object {
        /** 非零碰撞箱邊長。 */
        const val SIZE: Float = 0.1f

        /** 聲音在排程時間之後仍可播放的重載寬限。 */
        private const val SOUND_GRACE_TICKS: Long = 20L

        /** 同步所屬麻將桌 UUID。 */
        private val TABLE_ID: EntityDataAccessor<String> =
            SynchedEntityData.defineId(MahjongSoundTimelineEntity::class.java, EntityDataSerializers.STRING)
    }
}
