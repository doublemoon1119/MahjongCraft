package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.answerKeepAlive
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.newServerPlayer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.placeNewPlayer
import com.mojang.authlib.GameProfile
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil
import net.minecraft.core.UUIDUtil
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * 伺服器自動測試的機器人玩家。
 *
 * - 經原版的玩家加入流程進入伺服器，在玩家清單中與真人玩家一樣是線上玩家。
 * - 連線接在記憶體中的通道上，伺服器送出的封包直接丟掉。
 * - 只回覆伺服器的連線確認，機器人不會因為沒有回應而被斷線。
 * - 名稱固定，離線模式下的 UUID 由名稱決定，伺服器重新啟動後以同一個名稱加入就是同一位玩家。
 */
internal object AutotestBotPlayer {
    /**
     * 讓一位機器人玩家加入伺服器。需要在伺服器主執行緒上呼叫。
     *
     * @param server 目前伺服器。
     * @param name 玩家名稱。
     * @return 已加入的玩家。
     */
    fun join(server: MinecraftServer, name: String): ServerPlayer {
        val player = newServerPlayer(server, GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name))
        val connection = Connection(PacketFlow.SERVERBOUND)
        // 通道建立時會通知連線它已經連上；送往機器人的封包經過丟棄的處理器，不會累積在記憶體中。
        EmbeddedChannel(DiscardingOutbound(player), connection)
        placeNewPlayer(server, connection, player)
        return player
    }

    /**
     * 丟掉送往機器人的封包。遇到連線確認時代替用戶端回覆。
     *
     * @property player 這條連線的玩家。
     */
    private class DiscardingOutbound(private val player: ServerPlayer) : ChannelOutboundHandlerAdapter() {
        override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
            answerKeepAlive(player, message)
            ReferenceCountUtil.release(message)
            promise.setSuccess()
        }
    }
}
