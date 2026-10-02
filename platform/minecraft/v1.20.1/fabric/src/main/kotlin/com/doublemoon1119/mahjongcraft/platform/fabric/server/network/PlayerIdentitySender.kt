package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerIdentityEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerIdentityPayload
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 將已授權快照或查詢結果涉及的真人名稱分批同步，不提供任意 UUID 查詢入口。
 *
 * @property names 目前存檔的最後已知玩家名稱。
 * @property serverHolder 查找目前有效的收件玩家。
 * @property dispatchers 伺服器主執行緒排程。
 * @property json 線路序列化設定。
 */
@Single
class PlayerIdentitySender(
    private val names: ServerPlayerIdentityStore,
    private val serverHolder: FabricServerHolder,
    private val dispatchers: CoroutineDispatchers,
    private val json: Json,
) {
    /**
     * 只同步呼叫端已授權內容涉及的真人，所有原生名稱讀取均在主執行緒執行。
     *
     * @param targetId 已驗證的收件玩家。
     * @param playerIds 已授權內容中的真人 UUID，不得傳入全伺服器名單。
     */
    suspend fun send(targetId: Uuid, playerIds: Collection<Uuid>) = withContext(dispatchers.main) {
        val player = serverHolder.findPlayer(targetId) ?: return@withContext
        val entries = playerIds.distinct().mapNotNull { id -> names.resolveKnownName(id)?.let { PlayerIdentityEntry(id.toString(), it) } }
        entries.chunked(PlayerIdentityPayload.MAX_ENTRIES).forEach { batch ->
            MahjongChannels.playerIdentity.sendTo(player, json, PlayerIdentityPayload(batch))
        }
    }
}
