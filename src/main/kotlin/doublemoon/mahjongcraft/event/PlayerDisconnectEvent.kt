package doublemoon.mahjongcraft.event

import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGame
import doublemoon.mahjongcraft.network.MahjongTablePacketHandler
import net.minecraft.server.network.ServerPlayerEntity

/**
 * 玩家登出事件,
 * 用來控制登出後對遊戲進行的處理
 * */
fun onPlayerDisconnect(player: ServerPlayerEntity) {
    if (!player.world.isClient && GameManager.isInAnyGame(player)) {
        when (val game = GameManager.getGameBy(player) ?: return) {
            is MahjongGame -> {
                MahjongTablePacketHandler.syncBlockEntityWithGame(game = game) {
                    onPlayerLoggedOut(player)
                }
            }
        }
    }
}