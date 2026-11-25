package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla

import com.mojang.authlib.GameProfile
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * 建立一位還沒加入伺服器的玩家，使用原版的預設用戶端設定。
 *
 * @param server 目前伺服器。
 * @param profile 玩家 profile。
 * @return 在 overworld 的新玩家。
 */
fun newServerPlayer(server: MinecraftServer, profile: GameProfile): ServerPlayer = ServerPlayer(server, server.overworld(), profile)

/**
 * 經原版的玩家加入流程讓玩家進入伺服器。需要在伺服器主執行緒上呼叫。
 *
 * @param server 目前伺服器。
 * @param connection 玩家的連線。
 * @param player 要加入的玩家。
 */
fun placeNewPlayer(server: MinecraftServer, connection: Connection, player: ServerPlayer) {
    server.playerList.placeNewPlayer(connection, player)
}

/**
 * 送往玩家的封包是連線確認時，代替用戶端回覆。其他封包不處理。
 *
 * @param player 這條連線的玩家。
 * @param packet 送往玩家的封包。
 */
fun answerKeepAlive(player: ServerPlayer, packet: Any) {
    if (packet is ClientboundKeepAlivePacket) player.connection.handleKeepAlive(ServerboundKeepAlivePacket(packet.id))
}
