package com.doublemoon1119.mahjongcraft.platform.fabric.server

import com.doublemoon1119.mahjongcraft.platform.fabric.MahjongCraftMod
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerThreadCoroutineDispatcher
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 持有目前運行中的 [MinecraftServer]，供 Koin 綁定的 `GameEventPublisher`/`RoomEventPublisher` 用
 * [Uuid] 查找對應的 [ServerPlayerEntity] 送封包，也供 [ServerThreadCoroutineDispatcher]
 * 把協程排程丟回伺服器主執行緒。set/clear 時機由 [MahjongCraftMod] 掛在
 * `ServerLifecycleEvents.SERVER_STARTED`/`SERVER_STOPPING`。
 */
@Single
class FabricServerHolder {
    /** 目前由 Fabric lifecycle callback 設定的 server。 */
    private var server: MinecraftServer? = null

    /**
     * 設定目前運行中的 server。
     *
     * @param server Fabric lifecycle callback 提供的 server。
     */
    fun set(server: MinecraftServer) {
        this.server = server
    }

    /** 清除目前 server；server 停止時呼叫。 */
    fun clear() {
        server = null
    }

    /**
     * 取得目前伺服器。
     *
     * @return 已設定的伺服器；尚未設定或已清除時為 null。
     */
    fun current(): MinecraftServer? = server

    /**
     * 依玩家 UUID 查找目前在線玩家。
     *
     * @param playerId 要查找的玩家 UUID。
     * @return 找到的玩家，或玩家不在線／server 不存在時的 null。
     */
    fun findPlayer(playerId: Uuid): ServerPlayerEntity? = server?.playerManager?.getPlayer(playerId.toJavaUuid())
}
