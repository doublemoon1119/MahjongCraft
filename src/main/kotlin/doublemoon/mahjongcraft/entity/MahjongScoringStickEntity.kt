package doublemoon.mahjongcraft.entity

import doublemoon.mahjongcraft.game.mahjong.riichi.ScoringStick
import doublemoon.mahjongcraft.registry.EntityTypeRegistry
import doublemoon.mahjongcraft.registry.ItemRegistry
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World

class MahjongScoringStickEntity(
    type: EntityType<*> = EntityTypeRegistry.mahjongScoringStick,
    world: World
) : GameEntity(type, world) {

    /**
     * 點棒編號 (0~3),
     * 控制點棒外觀
     * */
    var code: Int
        set(value) = dataTracker.set(CODE, value)
        get() = dataTracker[CODE]

    val scoringStick: ScoringStick
        get() = ScoringStick.values()[code]

    override fun isCollidable(): Boolean = true

    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (!player.world.isClient) {
            player as ServerPlayerEntity
            if (!isSpawnedByGame && player.isSneaking) {  //不是由遊戲產生, 是手動放置的一般實體
                //有蹲下, 把棒子撿起來
                val item = ItemRegistry.mahjongScoringStick.defaultStack.also { it.damage = code }
                player.giveItemStack(item)
                playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1f, 1f)
                remove()
            }
        }
        return ActionResult.SUCCESS
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(CODE, ScoringStick.P100.code)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        code = nbt.getInt("Code")
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.putInt("Code", code)
    }

    companion object {
        const val MAHJONG_POINT_STICK_SCALE = 0.4f
        const val MAHJONG_POINT_STICK_WIDTH = 1f / 16 * 16 * MAHJONG_POINT_STICK_SCALE
        const val MAHJONG_POINT_STICK_HEIGHT = 1f / 16 * 0.5f * MAHJONG_POINT_STICK_SCALE
        const val MAHJONG_POINT_STICK_DEPTH = 1f / 16 * 2.5f * MAHJONG_POINT_STICK_SCALE
//        const val MAHJONG_POINT_STICK_SMALL_PADDING = 0.0025

        private val CODE: TrackedData<Int> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
    }
}