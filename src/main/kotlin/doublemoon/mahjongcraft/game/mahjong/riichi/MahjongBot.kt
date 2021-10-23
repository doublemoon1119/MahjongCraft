package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.entity.MahjongBotEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * 麻將機器人,
 * 使用 [MahjongGame.addBot] 的時候就會生成 [entity] 了 (並不會遊戲開始才生成)
 * TODO 待玩家功能完成後再回來補全電腦的 AI
 * @param world 預設生成的世界
 * @param pos 預設生成的位置
 * @param gamePos 生成這個機器人的遊戲的方塊座標,
 * */
class MahjongBot(
    val world: ServerWorld,
    pos: Vec3d,
    gamePos: BlockPos,
) : MahjongPlayerBase() {

    override val entity: MahjongBotEntity = MahjongBotEntity(world = world).apply {
        code = MahjongTile.random().code //隨機決定外觀
        isSpawnedByGame = true
        gameBlockPos = gamePos
        isInvisible = true //先隱形, 等開始遊戲才解除隱形
        refreshPositionAfterTeleport(pos)  //再傳送到預設位置上
        world.spawnEntity(this) //最後才在世界上生成
    }

    override var ready: Boolean = true

    override fun teleport(targetWorld: ServerWorld, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        with(entity) {
            if (this.world != targetWorld) setWorld(targetWorld)
            this.yaw = yaw
            this.pitch = pitch
            requestTeleport(x, y, z)
        }
    }
}