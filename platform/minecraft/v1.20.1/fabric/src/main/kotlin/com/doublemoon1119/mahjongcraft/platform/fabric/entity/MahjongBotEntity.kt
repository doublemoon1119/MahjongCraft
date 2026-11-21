package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 牌體加上短手腳的麻將機器人；只提供世界呈現，沒有尋路、戰鬥或牌局決策。
 *
 * 此類型不寫入區塊存檔；角色由權威對局恢復。同步欄位只有公開外觀與視覺動作，不包含遊戲手牌。
 *
 * @param type 原生實體類型。
 * @param world 所屬世界。
 */
class MahjongBotEntity(
    type: EntityType<out MahjongBotEntity> = ModEntities.mahjongBot,
    world: Level,
) : Entity(type, world) {
    /** 公開角色牌面；不是任何正式牌張的牌面。 */
    var faceAssetKey: String
        get() = entityData.get(FACE_ASSET)
        set(value) = entityData.set(FACE_ASSET, value)

    /** 目前固定席位的朝向；牌體保持直立並面向桌子。 */
    var seatYaw: Float = 0f

    /** 本機收到最近一次揮手時的實體年齡。 */
    private var swingAge: Int = -MahjongBotArmAnimation.DURATION_TICKS

    init {
        setNoGravity(true)
    }

    /** 同步公開外觀及揮手序號，不同步能力來源或私有決策。 */
    override fun defineSynchedData() {
        entityData.define(FACE_ASSET, UNKNOWN_TILE_ASSET_KEY)
        entityData.define(SWING_SEQUENCE, 0)
    }

    /**
     * 收到揮手序號後在本機開始短暫動作。
     *
     * @param data 已改變的原生同步欄位。
     */
    override fun onSyncedDataUpdated(data: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(data)
        if (data == SWING_SEQUENCE && tickCount > 0) swingAge = tickCount
    }

    /** 保持固定席位朝向及直立牌體，不自行轉看目標或移動位置。 */
    override fun tick() {
        super.tick()
        setDeltaMovement(Vec3.ZERO)
        if (level().isClientSide) return
        setYRot(seatYaw)
        setXRot(0f)
    }

    /** 通知追蹤此角色的客戶端播放一次短暫揮手。 */
    fun swingHand() {
        entityData.set(SWING_SEQUENCE, entityData.get(SWING_SEQUENCE) + 1)
    }

    /**
     * 取得本機揮手進度。
     *
     * @param tickDelta 本幀 tick 進度。
     * @return 介於 0 與 1，動作結束時為 1。
     */
    fun swingProgress(tickDelta: Float): Float = ((tickCount - swingAge + tickDelta) / MahjongBotArmAnimation.DURATION_TICKS).coerceIn(0f, 1f)

    /** 不接受傷害，不讓攻擊改變角色接管或權威對局。 */
    override fun hurt(source: DamageSource, amount: Float): Boolean = false

    /** 可被滑鼠射線命中，供座位物品互動使用；受擊仍不造成傷害。 */
    override fun isPickable(): Boolean = true

    /** 不讓推擠改變固定席位。 */
    override fun isPushable(): Boolean = false

    /** 保留身體碰撞供安全席位查詢辨識。 */
    override fun canBeCollidedWith(): Boolean = true

    /** 角色由牌局恢復，不保存自訂世界狀態。 */
    override fun addAdditionalSaveData(nbt: CompoundTag) = Unit

    /** 不從區塊 NBT 恢復代理。 */
    override fun readAdditionalSaveData(nbt: CompoundTag) = Unit

    /** 公開模型尺寸及本機動畫常數。 */
    companion object {
        /** 原生碰撞寬度。 */
        const val WIDTH: Float = 0.6f

        /** 原生碰撞高度。 */
        const val HEIGHT: Float = 1.5f

        /** 公開外觀同步欄位。 */
        private val FACE_ASSET = SynchedEntityData.defineId(MahjongBotEntity::class.java, EntityDataSerializers.STRING)

        /** 視覺揮手同步欄位。 */
        private val SWING_SEQUENCE = SynchedEntityData.defineId(MahjongBotEntity::class.java, EntityDataSerializers.INT)
    }
}
