package doublemoon.mahjongcraft.event

import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGame
import doublemoon.mahjongcraft.network.MahjongTablePacketHandler
import doublemoon.mahjongcraft.game.GameManager
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.registry.RegistryKey
import net.minecraft.world.World

/**
 * 玩家切換維度(世界)事件,
 * 用來切換世界後對遊戲進行的處理
 * */
fun onPlayerChangedDimension(player: PlayerEntity, fromDim: RegistryKey<World>, toDim: RegistryKey<World>) {
    if (fromDim == toDim) return
    if (!player.world.isClient && GameManager.isInAnyGame(player as ServerPlayerEntity)) {
        when (val game = GameManager.getGameBy(player) ?: return) {
            is MahjongGame -> {
                MahjongTablePacketHandler.syncBlockEntityWithGame(game = game) {
                    onPlayerChangedDimension(player)
                }
            }
        }
    }
}