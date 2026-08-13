package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World

/** 可自由放置並保存朝上點數的麻將骰子 entity。 */
class MahjongDiceEntity(
    type: EntityType<out MahjongDiceEntity> = ModEntities.mahjongDice,
    world: World,
) : Entity(type, world) {
    /** 目前朝上的點數；非法 tracked value 使用一點。 */
    var point: MahjongDicePoint
        get() = MahjongDicePoint.fromValueOrDefault(dataTracker[POINT])
        set(value) = dataTracker.set(POINT, value.value)

    /** 是否由正式牌局管理；管理中的骰子不接受測試互動。 */
    var managedByGame: Boolean
        get() = dataTracker[MANAGED_BY_GAME]
        set(value) = dataTracker.set(MANAGED_BY_GAME, value)

    init {
        setNoGravity(true)
    }

    /** 骰子只提供視線選取，不阻擋玩家或其他 entity。 */
    override fun isCollidable(): Boolean = false

    /** 骰子不參與一般 entity 推擠。 */
    override fun isPushable(): Boolean = false

    /** 允許 raycast、右鍵互動與後續 HUD targeting 選取骰子。 */
    override fun canHit(): Boolean = !isRemoved

    /** 允許自由放置骰子成為左鍵回收目標。 */
    override fun isAttackable(): Boolean = !isRemoved && !managedByGame

    /** 右鍵自由放置骰子時循環朝上點數。 */
    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (managedByGame) return ActionResult.PASS
        if (!world.isClient) point = point.next()
        return if (world.isClient) ActionResult.SUCCESS else ActionResult.CONSUME
    }

    /** 左鍵回收自由放置骰子；創造模式只移除 entity。 */
    override fun handleAttack(attacker: Entity): Boolean {
        val player = attacker as? PlayerEntity ?: return false
        if (managedByGame) return false
        if (!world.isClient) {
            if (!player.abilities.creativeMode) dropStack(ItemStack(ModItems.MAHJONG_DICE))
            playSound(SoundEvents.ENTITY_ITEM_FRAME_BREAK, 1.0f, 1.0f)
            discard()
        }
        return true
    }

    /** 初始化 client/server 同步的點數與管理狀態。 */
    override fun initDataTracker() {
        dataTracker.startTracking(POINT, MahjongDicePoint.ONE.value)
        dataTracker.startTracking(MANAGED_BY_GAME, false)
    }

    /** 從世界存檔還原點數及管理狀態；無效點數使用一點。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        point = MahjongDicePoint.fromValueOrDefault(nbt.getInt(NBT_KEY_POINT))
        managedByGame = nbt.getBoolean(NBT_KEY_MANAGED_BY_GAME)
    }

    /** 將點數及管理狀態寫入世界存檔。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putInt(NBT_KEY_POINT, point.value)
        nbt.putBoolean(NBT_KEY_MANAGED_BY_GAME, managedByGame)
    }

    companion object {
        /** 舊版驗證過的骰子世界邊長。 */
        const val SIZE = 0.125f

        /** 點數世界存檔 key。 */
        private const val NBT_KEY_POINT = "Point"

        /** 正式牌局管理狀態世界存檔 key。 */
        private const val NBT_KEY_MANAGED_BY_GAME = "ManagedByGame"

        /** 同步目前朝上的點數值。 */
        private val POINT: TrackedData<Int> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步是否由正式牌局管理。 */
        private val MANAGED_BY_GAME: TrackedData<Boolean> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
    }
}
