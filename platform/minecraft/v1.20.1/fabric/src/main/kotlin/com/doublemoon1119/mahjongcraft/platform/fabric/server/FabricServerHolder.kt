package com.doublemoon1119.mahjongcraft.platform.fabric.server

import com.doublemoon1119.mahjongcraft.platform.fabric.MahjongCraftMod
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerThreadCoroutineDispatcher
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 持有目前運行中的 [MinecraftServer]，供 Koin 綁定的 `GameEventPublisher`/`RoomEventPublisher` 用
 * [Uuid] 查找對應的 [ServerPlayerEntity] 送封包，也供 [ServerThreadCoroutineDispatcher]
 * 把協程排程丟回伺服器主執行緒。set/clear 時機由 [MahjongCraftMod] 掛在
 * `ServerLifecycleEvents.SERVER_STARTED`/`SERVER_STOPPING`。
 */
class FabricServerHolder {
    /** 一次發布伺服器與啟動身分，避免讀取到兩次啟動混合的資料。 */
    @Volatile
    private var activeSession: ActiveSession? = null

    /**
     * 設定目前運行中的 server。
     *
     * @param server Fabric lifecycle callback 提供的 server。
     */
    fun set(server: MinecraftServer) {
        activeSession = ActiveSession(server, Uuid.random())
    }

    /** 清除目前 server；server 停止時呼叫。 */
    fun clear() {
        activeSession = null
    }

    /**
     * 取得目前伺服器。
     *
     * @return 已設定的伺服器；尚未設定或已清除時為 null。
     */
    fun current(): MinecraftServer? = activeSession?.server

    /**
     * 取得目前啟動身分，供排程工作拒絕舊世界來源。
     *
     * @return 目前啟動身分；未啟動或已停止時為 null。
     */
    fun sessionId(): Uuid? = activeSession?.id

    /**
     * 依玩家 UUID 查找目前在線玩家。
     *
     * @param playerId 要查找的玩家 UUID。
     * @return 找到的玩家，或玩家不在線／server 不存在時的 null。
     */
    fun findPlayer(playerId: Uuid): ServerPlayerEntity? = activeSession?.server?.playerManager?.getPlayer(playerId.toJavaUuid())

    /**
     * 一次伺服器啟動的不可變身分。
     *
     * @property server 目前有效的伺服器。
     * @property id 每次啟動都重新建立的識別碼。
     */
    private class ActiveSession(val server: MinecraftServer, val id: Uuid)
}
