package doublemoon.mahjongcraft.game

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

object GameManager {

    /**
     * 根據 [BlockPos] 方塊的位置, 儲存各種遊戲
     * */
    val games = mutableListOf<GameBase<*>>()

    /**
     * 判斷 [player] 是否在任意遊戲中
     * */
    fun isInAnyGame(player: ServerPlayerEntity): Boolean =
        getGameBy(player) != null

    /**
     * 根據 [player] 取得遊戲
     * */
    fun getGameBy(player: ServerPlayerEntity): GameBase<*>? =
        games.find { it.getPlayer(player) != null }

    /**
     * 根據 [player] 取得 [games] 裡的遊戲, 沒有就回傳 null
     * */
    inline fun <reified T : GameBase<*>> getGame(player: ServerPlayerEntity): T? =
        games.filterIsInstance<T>().find { it.getPlayer(player) != null }

    /**
     * 根據 [world] 和 [pos] 取得 [games] 裡的遊戲, 沒有就回傳 null
     * */
    inline fun <reified T : GameBase<*>> getGame(world: ServerWorld, pos: BlockPos): T? =
        games.filterIsInstance<T>().find { it.world == world && it.pos == pos }

    /**
     * 根據 [world] 和 [pos] 取得 [games] 裡的遊戲, 沒有就用 [default] 建立後存放在 [games] 並回傳 [default]
     * */
    inline fun <reified T : GameBase<*>> getGameOrDefault(world: ServerWorld, pos: BlockPos, default: T): T =
        getGame(world, pos) ?: run { default.also { games += it } }
}