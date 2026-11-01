package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

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
    world: World,
) : Entity(type, world) {
    /** 公開角色牌面；不是任何正式牌張的牌面。 */
    var faceAssetKey: String
        get() = dataTracker.get(FACE_ASSET)
        set(value) = dataTracker.set(FACE_ASSET, value)

    /** 目前固定席位的朝向；牌體保持直立並面向桌子。 */
    var seatYaw: Float = 0f

    /** 本機收到最近一次揮手時的實體年齡。 */
    private var swingAge: Int = -MahjongBotArmAnimation.DURATION_TICKS

    init {
        setNoGravity(true)
    }

    /** 同步公開外觀及揮手序號，不同步能力來源或私有決策。 */
    override fun initDataTracker() {
        dataTracker.startTracking(FACE_ASSET, UNKNOWN_TILE_ASSET_KEY)
        dataTracker.startTracking(SWING_SEQUENCE, 0)
    }

    /**
     * 收到揮手序號後在本機開始短暫動作。
     *
     * @param data 已改變的原生同步欄位。
     */
    override fun onTrackedDataSet(data: TrackedData<*>) {
        super.onTrackedDataSet(data)
        if (data == SWING_SEQUENCE && age > 0) swingAge = age
    }

    /** 保持固定席位朝向及直立牌體，不自行轉看目標或移動位置。 */
    override fun tick() {
        super.tick()
        velocity = Vec3d.ZERO
        if (world.isClient) return
        yaw = seatYaw
        pitch = 0f
    }

    /** 通知追蹤此角色的客戶端播放一次短暫揮手。 */
    fun swingHand() {
        dataTracker.set(SWING_SEQUENCE, dataTracker.get(SWING_SEQUENCE) + 1)
    }

    /**
     * 取得本機揮手進度。
     *
     * @param tickDelta 本幀 tick 進度。
     * @return 介於 0 與 1，動作結束時為 1。
     */
    fun swingProgress(tickDelta: Float): Float = ((age - swingAge + tickDelta) / MahjongBotArmAnimation.DURATION_TICKS).coerceIn(0f, 1f)

    /** 不接受傷害，不讓攻擊改變角色接管或權威對局。 */
    override fun damage(source: DamageSource, amount: Float): Boolean = false

    /** 不讓推擠改變固定席位。 */
    override fun isPushable(): Boolean = false

    /** 保留身體碰撞供安全席位查詢辨識。 */
    override fun isCollidable(): Boolean = true

    /** 角色由牌局恢復，不保存自訂世界狀態。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit

    /** 不從區塊 NBT 恢復代理。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit

    /** 公開模型尺寸及本機動畫常數。 */
    companion object {
        /** 原生碰撞寬度。 */
        const val WIDTH: Float = 0.6f

        /** 原生碰撞高度。 */
        const val HEIGHT: Float = 1.5f

        /** 公開外觀同步欄位。 */
        private val FACE_ASSET = DataTracker.registerData(MahjongBotEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 視覺揮手同步欄位。 */
        private val SWING_SEQUENCE = DataTracker.registerData(MahjongBotEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
    }
}
