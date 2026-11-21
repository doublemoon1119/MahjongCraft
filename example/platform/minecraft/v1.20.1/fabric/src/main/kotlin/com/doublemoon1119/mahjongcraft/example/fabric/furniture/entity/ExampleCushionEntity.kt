package com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureItems
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleCushionPlacement
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleCushionSeatService
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleFurnitureSeatRules
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level

/**
 * 範例可保存坐墊；entity 本身就是第三方座位目標。
 *
 * @param type Minecraft entity 類型。
 * @param world 實體所屬世界。
 */
class ExampleCushionEntity(
    type: EntityType<out ExampleCushionEntity>,
    world: Level,
) : Entity(type, world) {
    init {
        setNoGravity(true)
    }

    /** 坐墊不參與一般實體推擠。 */
    override fun isPushable(): Boolean = false

    /** 坐墊不提供實體碰撞。 */
    override fun canBeCollidedWith(): Boolean = false

    /** 即使坐墊不參與碰撞，也允許玩家用準星選取它來互動。 */
    override fun isPickable(): Boolean = !isRemoved

    /** 右鍵坐下；潛行右鍵不移除家具。 */
    override fun interact(player: Player, hand: InteractionHand): InteractionResult {
        if (player.isShiftKeyDown) return InteractionResult.PASS
        if (level().isClientSide) return InteractionResult.SUCCESS
        val serverWorld = level() as? ServerLevel ?: return InteractionResult.FAIL
        if (isRemoved || !serverWorld.chunkSource.hasChunk(blockX shr 4, blockZ shr 4)) return InteractionResult.FAIL
        return if (ExampleCushionSeatService.sit(serverWorld, this, player, player.yRot)) InteractionResult.CONSUME else InteractionResult.FAIL
    }

    /**
     * 玩家直接左鍵攻擊時拆除空坐墊，不接受投射物或其他傷害代替拆除。
     *
     * @param source 傷害來源，須為可修改此位置的玩家直接攻擊。
     * @param amount 傷害量；拆除不依傷害量累積。
     * @return 完成拆除時為 true；有人乘坐或權限不足時為 false。
     */
    override fun hurt(source: DamageSource, amount: Float): Boolean {
        val serverWorld = level() as? ServerLevel ?: return false
        val player = source.entity as? Player ?: return false
        if (source.directEntity !== player || !serverWorld.chunkSource.hasChunk(blockX shr 4, blockZ shr 4)) return false
        val canModify = player.abilities.mayBuild && serverWorld.mayInteract(player, blockPosition())
        if (!ExampleFurnitureSeatRules.canBreakCushion(isRemoved, isVehicle, canModify)) return false
        removeCushion(dropItem = !player.abilities.instabuild)
        return true
    }

    /** 每秒檢查一次支撐表面；失去支撐時讓乘客下座並移除坐墊。 */
    override fun tick() {
        super.tick()
        val serverWorld = level() as? ServerLevel ?: return
        if (!isRemoved && tickCount % SUPPORT_CHECK_TICKS == 0 && ExampleCushionPlacement.hasSupport(serverWorld, boundingBox) == false) {
            removeCushion(dropItem = true)
        }
    }

    /**
     * 移除坐墊並至多掉落一個物品，重複呼叫不會再次掉落。
     *
     * @param dropItem 是否掉落物品，仍遵守世界的實體掉落規則。
     */
    private fun removeCushion(dropItem: Boolean) {
        if (isRemoved) return
        ejectPassengers()
        discard()
        if (dropItem && level().gameRules.getBoolean(GameRules.RULE_DOENTITYDROPS)) spawnAtLocation(ItemStack(ExampleFurnitureItems.cushion))
    }

    /** 坐墊 entity 沒有額外的持久化欄位；UUID 與位置由原版保存。 */
    override fun readAdditionalSaveData(nbt: CompoundTag) = Unit

    /** 坐墊 entity 沒有額外的持久化欄位；UUID 與位置由原版保存。 */
    override fun addAdditionalSaveData(nbt: CompoundTag) = Unit

    /** 坐墊不需要資料追蹤欄位。 */
    override fun defineSynchedData() = Unit

    /** 依坐姿高度定位乘客，使坐姿模型落在坐墊表面。 */
    override fun positionRider(passenger: Entity, positionUpdater: MoveFunction) {
        if (hasPassenger(passenger)) {
            positionUpdater.accept(passenger, x, ExampleSeatGeometry.cushionFeetY(y), z)
        }
    }

    /** 坐墊的支撐檢查間隔。 */
    private companion object {
        /** 以一般伺服器節奏每秒檢查一次。 */
        const val SUPPORT_CHECK_TICKS = 20
    }
}
