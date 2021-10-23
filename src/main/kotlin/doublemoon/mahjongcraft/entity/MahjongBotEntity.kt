package doublemoon.mahjongcraft.entity

import doublemoon.mahjongcraft.blockentity.MahjongTableBlockEntity
import doublemoon.mahjongcraft.entity.MahjongTileEntity.Companion.MAHJONG_TILE_HEIGHT
import doublemoon.mahjongcraft.entity.MahjongTileEntity.Companion.MAHJONG_TILE_SCALE
import doublemoon.mahjongcraft.entity.MahjongTileEntity.Companion.MAHJONG_TILE_WIDTH
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongBot
import doublemoon.mahjongcraft.registry.EntityTypeRegistry
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World

/**
 * 麻將機器人 [MahjongBot] 的實體,
 * (目前就只是一個比較大的麻將牌)
 * */
class MahjongBotEntity(
    type: EntityType<*> = EntityTypeRegistry.mahjongBot,
    world: World
) : GameEntity(type, world) {

    /**
     * 暫時與 [MahjongTileEntity] 一樣, 都是顯示麻將牌外觀的
     * */
    var code: Int
        set(value) = dataTracker.set(CODE, value)
        get() = dataTracker[CODE]

    init {
        isInvulnerable = true
    }

    //可以看到實體才有實體碰撞
    override fun isCollidable(): Boolean = !isInvisible

    override fun collides(): Boolean = !isInvisible

    //目前機器人實體在麻將桌按下 加入機器人 的時候 (即遊戲還沒開始的時候) 就存在了,
    override fun autoRemove() {
        if (!world.isClient && isSpawnedByGame && isAlive) {
            val blockEntity = world.getBlockEntity(gameBlockPos)
            if (blockEntity !is MahjongTableBlockEntity) {  //這方塊不是麻將桌
                remove()
            }
        }
    }

    override fun shouldRenderName(): Boolean = true

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(CODE, 0)
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
        const val MAHJONG_BOT_WIDTH = MAHJONG_TILE_WIDTH / MAHJONG_TILE_SCALE
        const val MAHJONG_BOT_HEIGHT = MAHJONG_TILE_HEIGHT / MAHJONG_TILE_SCALE

        private val CODE: TrackedData<Int> =
            DataTracker.registerData(MahjongBotEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
    }


}