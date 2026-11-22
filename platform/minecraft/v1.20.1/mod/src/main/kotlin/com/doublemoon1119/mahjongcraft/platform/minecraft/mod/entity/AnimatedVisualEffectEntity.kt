package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 可持久化、可由絕對世界時間任意取樣的純視覺 entity 基底。效果種類、目標 entity、隨機 seed 與時間窗
 * 都同時透過 tracked data 同步給 client、透過 NBT 保存；具體 renderer 只需要依 [effectKey] 與
 * [progress] 選擇視覺公式，不必各自重寫生命週期與重載恢復。
 *
 * @param type 特效實體種類。
 * @param world 實體所屬世界。
 */
abstract class AnimatedVisualEffectEntity(
    type: EntityType<out AnimatedVisualEffectEntity>,
    world: Level,
) : SimpleAnimatedMahjongEntity(type, world),
    TableOwnedPresentationEntity {
    /** 所屬麻將桌。 */
    override val managedTableId: Uuid?
        get() = entityData[TABLE_ID].takeIf(String::isNotBlank)?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }

    /** 選擇具體視覺公式的穩定 key。 */
    val effectKey: String
        get() = entityData[EFFECT_KEY]

    /** 效果跟隨的目標 entity UUID；資料尚未設定或已損壞時為 `null`。 */
    val targetEntityId: Uuid?
        get() = entityData[TARGET_ENTITY_ID]
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }

    /** renderer 產生重載前後一致圖樣使用的隨機 seed。 */
    val animationSeed: Long
        get() = entityData[ANIMATION_SEED]

    /** 效果開始的 server game time。 */
    val startGameTime: Long
        get() = entityData[START_GAME_TIME]

    /** 效果結束的 server game time。 */
    val endGameTime: Long
        get() = entityData[END_GAME_TIME]

    init {
        setNoGravity(true)
    }

    /**
     * 一次設定並同步完整效果描述；只能由伺服器在生成前呼叫。
     *
     * @param tableId 效果所屬麻將桌 UUID。
     * @param effectKey 選擇視覺公式的穩定 key。
     * @param targetEntityId 效果跟隨的目標 entity UUID。
     * @param animationSeed 重建視覺效果所用的隨機 seed。
     * @param startGameTime 效果開始的 server game time。
     * @param endGameTime 效果結束的 server game time，必須晚於開始時間。
     */
    fun configure(
        tableId: Uuid,
        effectKey: String,
        targetEntityId: Uuid,
        animationSeed: Long,
        startGameTime: Long,
        endGameTime: Long,
    ) {
        check(!level().isClientSide) { "Visual effects must be configured by the server" }
        require(effectKey.isNotBlank()) { "Visual effect key must not be blank" }
        require(endGameTime > startGameTime) { "Visual effect end time must be after start time" }
        entityData.set(TABLE_ID, tableId.toString())
        entityData.set(EFFECT_KEY, effectKey)
        entityData.set(TARGET_ENTITY_ID, targetEntityId.toString())
        entityData.set(ANIMATION_SEED, animationSeed)
        entityData.set(START_GAME_TIME, startGameTime)
        entityData.set(END_GAME_TIME, endGameTime)
    }

    /**
     * 指定畫面時間是否位於效果的可見時間窗內。
     *
     * @param tickDelta 本 tick 的部分經過時間。
     */
    fun isActive(tickDelta: Float): Boolean {
        val sampleTime = level().gameTime.toDouble() + tickDelta
        return endGameTime > startGameTime && sampleTime >= startGameTime && sampleTime < endGameTime
    }

    /**
     * 將指定畫面時間換算成 `0.0..1.0` 的效果進度；時間窗無效時回傳 `0.0`。
     *
     * @param tickDelta 本 tick 的部分經過時間。
     */
    fun progress(tickDelta: Float): Double {
        val duration = endGameTime - startGameTime
        if (duration <= 0L) return 0.0
        val elapsed = level().gameTime.toDouble() + tickDelta - startGameTime
        return (elapsed / duration).coerceIn(0.0, 1.0)
    }

    /** 純視覺 entity 不阻擋其他 entity。 */
    override fun canBeCollidedWith(): Boolean = false

    /** 純視覺 entity 不參與推擠。 */
    override fun isPushable(): Boolean = false

    /** 純視覺 entity 不成為 raycast 或互動目標。 */
    override fun isPickable(): Boolean = false

    /**
     * 伺服器端持續跟隨目標的實際位置，讓排程當下就能生成並保存等待中的效果；兩端到期後都主動移除，
     * client 不必等待額外移除封包才停止渲染。
     */
    override fun tick() {
        super.tick()
        if (!level().isClientSide) followTarget()
        if (endGameTime > startGameTime && level().gameTime >= endGameTime) discard()
    }

    /** 目標目前已載入時同步其位置；暫時找不到時保留最後位置等待後續 tick。 */
    private fun followTarget() {
        val serverWorld = level() as? ServerLevel ?: return
        val targetId = targetEntityId ?: return
        val target = serverWorld.getEntity(targetId.toJavaUuid()) ?: return
        moveTo(target.x, target.y, target.z, 0.0f, 0.0f)
    }

    /** 初始化所有視覺效果共用的 tracked data。 */
    override fun defineSynchedData() {
        entityData.define(TABLE_ID, "")
        entityData.define(EFFECT_KEY, "")
        entityData.define(TARGET_ENTITY_ID, "")
        entityData.define(ANIMATION_SEED, 0L)
        entityData.define(START_GAME_TIME, 0L)
        entityData.define(END_GAME_TIME, 0L)
    }

    /** 從世界存檔還原效果描述與絕對時間軸。 */
    override fun readAdditionalSaveData(nbt: CompoundTag) {
        entityData.set(TABLE_ID, ManagedTableNbt.read(nbt)?.toString().orEmpty())
        entityData.set(EFFECT_KEY, nbt.getString(NBT_KEY_EFFECT_KEY))
        entityData.set(TARGET_ENTITY_ID, nbt.getString(NBT_KEY_TARGET_ENTITY_ID))
        entityData.set(ANIMATION_SEED, nbt.getLong(NBT_KEY_ANIMATION_SEED))
        entityData.set(START_GAME_TIME, nbt.getLong(NBT_KEY_START_GAME_TIME))
        entityData.set(END_GAME_TIME, nbt.getLong(NBT_KEY_END_GAME_TIME))
        readAnimationQueueFromNbt(nbt)
    }

    /** 將效果描述與絕對時間軸寫入世界存檔；位置與 entity UUID 由原版 entity 序列化負責。 */
    override fun addAdditionalSaveData(nbt: CompoundTag) {
        ManagedTableNbt.write(nbt, managedTableId)
        nbt.putString(NBT_KEY_EFFECT_KEY, effectKey)
        nbt.putString(NBT_KEY_TARGET_ENTITY_ID, targetEntityId?.toString().orEmpty())
        nbt.putLong(NBT_KEY_ANIMATION_SEED, animationSeed)
        nbt.putLong(NBT_KEY_START_GAME_TIME, startGameTime)
        nbt.putLong(NBT_KEY_END_GAME_TIME, endGameTime)
        writeAnimationQueueToNbt(nbt)
    }

    companion object {
        /** 效果 key 的世界存檔 key。 */
        private const val NBT_KEY_EFFECT_KEY: String = "EffectKey"

        /** 目標 entity UUID 的世界存檔 key。 */
        private const val NBT_KEY_TARGET_ENTITY_ID: String = "TargetEntityId"

        /** 動畫 seed 的世界存檔 key。 */
        private const val NBT_KEY_ANIMATION_SEED: String = "AnimationSeed"

        /** 特效開始時間的世界存檔 key。 */
        private const val NBT_KEY_START_GAME_TIME: String = "StartGameTime"

        /** 特效結束時間的世界存檔 key。 */
        private const val NBT_KEY_END_GAME_TIME: String = "EndGameTime"

        /** 同步所屬麻將桌 UUID。 */
        private val TABLE_ID: EntityDataAccessor<String> =
            SynchedEntityData.defineId(AnimatedVisualEffectEntity::class.java, EntityDataSerializers.STRING)

        /** 同步效果 key。 */
        private val EFFECT_KEY: EntityDataAccessor<String> =
            SynchedEntityData.defineId(AnimatedVisualEffectEntity::class.java, EntityDataSerializers.STRING)

        /** 同步目標 entity UUID。 */
        private val TARGET_ENTITY_ID: EntityDataAccessor<String> =
            SynchedEntityData.defineId(AnimatedVisualEffectEntity::class.java, EntityDataSerializers.STRING)

        /** 同步動畫 seed。 */
        private val ANIMATION_SEED: EntityDataAccessor<Long> =
            SynchedEntityData.defineId(AnimatedVisualEffectEntity::class.java, EntityDataSerializers.LONG)

        /** 同步效果開始的 server game time。 */
        private val START_GAME_TIME: EntityDataAccessor<Long> =
            SynchedEntityData.defineId(AnimatedVisualEffectEntity::class.java, EntityDataSerializers.LONG)

        /** 同步效果結束的 server game time。 */
        private val END_GAME_TIME: EntityDataAccessor<Long> =
            SynchedEntityData.defineId(AnimatedVisualEffectEntity::class.java, EntityDataSerializers.LONG)
    }
}
