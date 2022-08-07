package doublemoon.mahjongcraft.entity

import doublemoon.mahjongcraft.blockentity.MahjongTableBlockEntity
import doublemoon.mahjongcraft.network.CustomEntitySpawnS2CPacketHandler
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.network.Packet
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

abstract class GameEntity(
    type: EntityType<*>,
    world: World
) : Entity(type, world) {

    // 沒這行選不到實體
    override fun canHit(): Boolean = !isRemoved

    /**
     * 遊戲所在的座標,
     * 不是遊戲產生的話會存入實體所在的 [blockPos]
     * */
    var gameBlockPos: BlockPos
        set(value) = dataTracker.set(GAME_BLOCK_POS, value)
        get() = dataTracker[GAME_BLOCK_POS]

    /**
     * 是否由遊戲產生
     * */
    open var isSpawnedByGame: Boolean
        set(value) = dataTracker.set(SPAWNED_BY_GAME, value)
        get() = dataTracker[SPAWNED_BY_GAME]

    /**
     * 當麻將桌 [MahjongTableBlockEntity] 不存在或者遊戲不是開始時自動移除
     * */
    open fun autoRemove() {
        if (!world.isClient && isSpawnedByGame && isAlive) {
            val blockEntity = world.getBlockEntity(gameBlockPos)
            if (blockEntity !is MahjongTableBlockEntity) {  //這方塊不是麻將桌
                remove(RemovalReason.DISCARDED)
            } else if (!blockEntity.playing) {  //這方塊是麻將桌, 沒有在遊玩狀態
                remove(RemovalReason.DISCARDED)
            }
        }
    }

    override fun tick() {
        super.tick()
        autoRemove()
    }

    override fun initDataTracker() {
        dataTracker.startTracking(GAME_BLOCK_POS, blockPos)
        dataTracker.startTracking(SPAWNED_BY_GAME, false)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        gameBlockPos = NbtHelper.toBlockPos(nbt.getCompound("GameBlockPos"))
        isSpawnedByGame = nbt.getBoolean("SpawnedByGame")
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.put("GameBlockPos", NbtHelper.fromBlockPos(gameBlockPos))
        nbt.putBoolean("SpawnedByGame", isSpawnedByGame)
    }

    override fun createSpawnPacket(): Packet<*> = CustomEntitySpawnS2CPacketHandler.createPacket(this)

    companion object {
        private val GAME_BLOCK_POS: TrackedData<BlockPos> = DataTracker.registerData(
            GameEntity::class.java,
            TrackedDataHandlerRegistry.BLOCK_POS
        )
        private val SPAWNED_BY_GAME: TrackedData<Boolean> =
            DataTracker.registerData(GameEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
    }
}