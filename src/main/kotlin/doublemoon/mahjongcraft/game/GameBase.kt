package doublemoon.mahjongcraft.game

import net.minecraft.particle.ParticleEffect
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

interface GameBase<T : GamePlayer> {
    /**
     * 遊戲內的玩家
     * */
    val players: MutableList<T>

    /**
     * 遊戲的名稱
     * */
    val name: Text

    /**
     * 遊戲所在的世界
     * */
    val world: ServerWorld

    /**
     * 遊戲所在的座標
     * */
    val pos: BlockPos

    /**
     * 遊戲的狀態
     * */
    var status: GameStatus

    /**
     * 讓 [player] 加入遊戲
     * */
    fun join(player: ServerPlayerEntity)

    /**
     * 讓 [player] 離開遊戲
     * */
    fun leave(player: ServerPlayerEntity)

    /**
     * 開始遊戲
     * */
    fun start(sync: Boolean = true)

    /**
     * 結束遊戲
     * */
    fun end(sync: Boolean = true)

    /**
     * 當遊戲被破壞時, ex: 破壞遊戲方塊
     * */
    fun onBreak()

    /**
     * 當玩家登出伺服器
     * */
    fun onPlayerDisconnect(player: ServerPlayerEntity)

    /**
     * 當玩家變更世界
     * */
    fun onPlayerChangedWorld(player: ServerPlayerEntity)

    /**
     * 當 [server] 關閉
     * */
    fun onServerStopping(server: MinecraftServer)

    /**
     * [player] 是否在遊戲內
     * */
    fun isInGame(player: ServerPlayerEntity): Boolean {
        return getPlayer(player) != null
    }

    /**
     * [player] 是否是這個遊戲的 Host
     * */
    fun isHost(player: ServerPlayerEntity): Boolean

    /**
     * 從 [players] 依照 [player] 取得 [T]
     * */
    fun getPlayer(player: ServerPlayerEntity): T? {
        return players.find { it.entity == player }
    }

    /**
     * 在 [pos] 的位置發出聲音
     *
     * @param pos 預設是麻將桌
     * */
    fun playSound(
        world: ServerWorld = this.world,
        pos: BlockPos = this@GameBase.pos,
        soundEvent: SoundEvent,
        category: SoundCategory = SoundCategory.VOICE,
        volume: Float = 1f,
        pitch: Float = 1f
    ) {
        world.playSound(
            null,
            pos,
            soundEvent,
            category,
            volume,
            pitch
        )
    }

    /**
     * 產生粒子效果
     * */
    fun spawnParticles(
        particleEffect: ParticleEffect,
        x: Double = pos.x + 0.5,
        y: Double = pos.y + 1.5,
        z: Double = pos.z + 0.5,
        count: Int = 1,
        deltaX: Double = 0.0,
        deltaY: Double = 0.0,
        deltaZ: Double = 0.0,
        speed: Double = 0.0
    ) {
        world.spawnParticles(particleEffect, x, y, z, count, deltaX, deltaY, deltaZ, speed)
    }

}