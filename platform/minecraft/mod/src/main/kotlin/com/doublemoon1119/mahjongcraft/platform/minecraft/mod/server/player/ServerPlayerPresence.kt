package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.player

import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerPresence
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.ServerHolder
import kotlin.uuid.Uuid

/**
 * 以伺服器目前的在線玩家名單回答玩家是否在線。
 *
 * @property serverHolder 查找目前伺服器上的在線玩家。伺服器尚未啟動或已停止時，每位玩家都算離線。
 */
class ServerPlayerPresence(private val serverHolder: ServerHolder) : PlayerPresence {
    override fun isOnline(playerId: Uuid): Boolean = serverHolder.findPlayer(playerId) != null
}
