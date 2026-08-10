package com.doublemoon1119.mahjongcraft.platform.fabric.server

import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 持有目前運行中的 [MinecraftServer]，供 Koin 綁定的 `GameEventPublisher`/`RoomEventPublisher` 用
 * [Uuid] 查找對應的 [ServerPlayerEntity] 送封包，也供
 * [com.doublemoon1119.mahjongcraft.platform.fabric.concurrency.ServerThreadCoroutineDispatcher]
 * 把協程排程丟回伺服器主執行緒。set/clear 時機由
 * [com.doublemoon1119.mahjongcraft.platform.fabric.MahjongCraftMod] 掛在
 * `ServerLifecycleEvents.SERVER_STARTED`/`SERVER_STOPPING`。
 */
@Single
class FabricServerHolder {
    private var server: MinecraftServer? = null

    fun set(server: MinecraftServer) {
        this.server = server
    }

    fun clear() {
        server = null
    }

    fun current(): MinecraftServer? = server

    fun findPlayer(playerId: Uuid): ServerPlayerEntity? = server?.playerManager?.getPlayer(playerId.toJavaUuid())
}
