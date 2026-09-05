package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import net.minecraft.entity.EntityType
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World

/** 從指定世界座標播放可持久化聲音時間線、播放完畢後自行移除的無形 entity。 */
class MahjongSoundTimelineEntity(
    type: EntityType<out MahjongSoundTimelineEntity> = ModEntities.mahjongSoundTimeline,
    world: World,
) : SimpleAnimatedMahjongEntity(type, world) {
    init {
        setNoGravity(true)
        isInvisible = true
    }

    /** 設定唯一一筆立即播放、短暫有效的聲音。 */
    fun configure(soundId: String, volume: Float, pitch: Float, playAtGameTime: Long) {
        check(!world.isClient) { "Sound timeline must be configured by the server" }
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

    override fun initDataTracker() = Unit

    override fun readCustomDataFromNbt(nbt: NbtCompound) = readAnimationQueueFromNbt(nbt)

    override fun writeCustomDataToNbt(nbt: NbtCompound) = writeAnimationQueueToNbt(nbt)

    /** 固定參數。 */
    companion object {
        /** 非零碰撞箱邊長。 */
        const val SIZE: Float = 0.1f

        /** 聲音在排程時間之後仍可播放的重載寬限。 */
        private const val SOUND_GRACE_TICKS: Long = 20L
    }
}
