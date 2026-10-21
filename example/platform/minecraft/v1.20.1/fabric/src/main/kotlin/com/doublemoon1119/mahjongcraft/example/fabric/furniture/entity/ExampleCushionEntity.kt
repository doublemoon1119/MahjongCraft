package com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureItems
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleCushionPlacement
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleFurnitureSeatRules
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleFurnitureSpace
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.Box
import net.minecraft.world.GameRules
import net.minecraft.world.World

/**
 * 範例可保存坐墊；entity 本身就是第三方座位目標。
 *
 * @param type Minecraft entity 類型。
 * @param world 實體所屬世界。
 */
class ExampleCushionEntity(
    type: EntityType<out ExampleCushionEntity>,
    world: World,
) : Entity(type, world) {
    init {
        setNoGravity(true)
    }

    /** 坐墊不參與一般實體推擠。 */
    override fun isPushable(): Boolean = false

    /** 坐墊不提供實體碰撞。 */
    override fun isCollidable(): Boolean = false

    /** 即使坐墊不參與碰撞，也允許玩家用準星選取它來互動。 */
    override fun canHit(): Boolean = !isRemoved

    /** 右鍵坐下；潛行右鍵不移除家具。 */
    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (player.isSneaking) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS
        val serverWorld = world as? ServerWorld ?: return ActionResult.FAIL
        if (isRemoved || !serverWorld.chunkManager.isChunkLoaded(blockX shr 4, blockZ shr 4)) return ActionResult.FAIL
        if (player.hasVehicle() || hasPassengers()) return ActionResult.CONSUME
        val feetY = ExampleSeatGeometry.cushionFeetY(y)
        val body = Box(
            x - player.width / 2.0,
            y + 0.2,
            z - player.width / 2.0,
            x + player.width / 2.0,
            feetY + player.height,
            z + player.width / 2.0,
        )
        if (!ExampleFurnitureSpace.isClear(serverWorld, player, body, this)) return ActionResult.CONSUME
        return if (player.startRiding(this, true)) ActionResult.CONSUME else ActionResult.FAIL
    }

    /**
     * 玩家直接左鍵攻擊時拆除空坐墊，不接受投射物或其他傷害代替拆除。
     *
     * @param source 傷害來源，須為可修改此位置的玩家直接攻擊。
     * @param amount 傷害量；拆除不依傷害量累積。
     * @return 完成拆除時為 true；有人乘坐或權限不足時為 false。
     */
    override fun damage(source: DamageSource, amount: Float): Boolean {
        val serverWorld = world as? ServerWorld ?: return false
        val player = source.attacker as? PlayerEntity ?: return false
        if (source.source !== player || !serverWorld.chunkManager.isChunkLoaded(blockX shr 4, blockZ shr 4)) return false
        val canModify = player.abilities.allowModifyWorld && serverWorld.canPlayerModifyAt(player, blockPos)
        if (!ExampleFurnitureSeatRules.canBreakCushion(isRemoved, hasPassengers(), canModify)) return false
        removeCushion(dropItem = !player.abilities.creativeMode)
        return true
    }

    /** 每秒檢查一次支撐表面；失去支撐時讓乘客下座並移除坐墊。 */
    override fun tick() {
        super.tick()
        val serverWorld = world as? ServerWorld ?: return
        if (!isRemoved && age % SUPPORT_CHECK_TICKS == 0 && ExampleCushionPlacement.hasSupport(serverWorld, boundingBox) == false) {
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
        removeAllPassengers()
        discard()
        if (dropItem && world.gameRules.getBoolean(GameRules.DO_ENTITY_DROPS)) dropStack(ItemStack(ExampleFurnitureItems.cushion))
    }

    /** 坐墊 entity 沒有額外的持久化欄位；UUID 與位置由原版保存。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit

    /** 坐墊 entity 沒有額外的持久化欄位；UUID 與位置由原版保存。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit

    /** 坐墊不需要資料追蹤欄位。 */
    override fun initDataTracker() = Unit

    /** 依坐姿高度定位乘客，使坐姿模型落在坐墊表面。 */
    override fun updatePassengerPosition(passenger: Entity, positionUpdater: PositionUpdater) {
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
