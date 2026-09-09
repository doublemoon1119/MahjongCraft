package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World
import kotlin.uuid.Uuid

/** 從指定世界座標播放可持久化聲音時間線、播放完畢後自行移除的無形 entity。 */
class MahjongSoundTimelineEntity(
    type: EntityType<out MahjongSoundTimelineEntity> = ModEntities.mahjongSoundTimeline,
    world: World,
) : SimpleAnimatedMahjongEntity(type, world),
    TableOwnedPresentationEntity {
    /** 所屬麻將桌。 */
    override val managedTableId: Uuid?
        get() = dataTracker[TABLE_ID].takeIf(String::isNotBlank)?.let { encoded ->
            runCatching { Uuid.parse(encoded) }.getOrNull()
        }

    init {
        setNoGravity(true)
        isInvisible = true
    }

    /** 設定唯一一筆立即播放、短暫有效的聲音。 */
    fun configure(tableId: Uuid, soundId: String, volume: Float, pitch: Float, playAtGameTime: Long) {
        check(!world.isClient) { "Sound timeline must be configured by the server" }
        dataTracker.set(TABLE_ID, tableId.toString())
        enqueue(
            AnimationStep.PlaySound(
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
        if (!world.isClient && !isAnimating) discard()
    }

    override fun initDataTracker() {
        dataTracker.startTracking(TABLE_ID, "")
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        dataTracker.set(TABLE_ID, nbt.getString(NBT_KEY_TABLE_ID))
        readAnimationQueueFromNbt(nbt)
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_KEY_TABLE_ID, managedTableId?.toString().orEmpty())
        writeAnimationQueueToNbt(nbt)
    }

    /** 固定參數。 */
    companion object {
        /** 所屬麻將桌 UUID 的世界存檔 key。 */
        private const val NBT_KEY_TABLE_ID: String = "ManagedTableId"

        /** 非零碰撞箱邊長。 */
        const val SIZE: Float = 0.1f

        /** 聲音在排程時間之後仍可播放的重載寬限。 */
        private const val SOUND_GRACE_TICKS: Long = 20L

        /** 同步所屬麻將桌 UUID。 */
        private val TABLE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongSoundTimelineEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}
