package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import net.minecraft.client.MinecraftClient
import org.koin.core.annotation.Single
import java.util.UUID
import kotlin.uuid.Uuid

/** 集中解析所有 HUD、hovered text 與世界面板使用的真人及 AI 顯示名稱。 */
@Single
class ClientPlayerDisplayNameResolver(
    private val stateStore: ClientMahjongStateStore,
) {
    /**
     * 以房間持久化 AI 順序為優先，無 lobby 時才退回目前遊戲快照順序。[tableId] 為 `null`（呼叫端一時
     * 拿不到管理中的桌子 ID）時，AI 順序退回空清單、真人名稱解析不受影響。
     */
    fun resolve(tableId: Uuid?, playerId: String, isAiHint: Boolean? = null): String {
        val id = runCatching { Uuid.parse(playerId) }.getOrNull() ?: return playerId
        val snapshotPlayer = tableId?.let(stateStore::gameSnapshot)?.players?.firstOrNull { it.id == id }
        val isAi = isAiHint ?: snapshotPlayer?.isAi == true
        if (isAi) return aiPlayerDisplayName(id, orderedAiPlayerIds(tableId))
        return runCatching { UUID.fromString(playerId) }.getOrNull()?.let { uuid ->
            MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(uuid)?.profile?.name
        } ?: playerId.take(8)
    }

    private fun orderedAiPlayerIds(tableId: Uuid?): List<Uuid> {
        if (tableId == null) return emptyList()
        val lobbyIds = stateStore.tableLobby(tableId)?.playingAiPlayerIds.orEmpty().mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
        if (lobbyIds.isNotEmpty()) return lobbyIds
        stateStore.roomSnapshot(tableId)?.aiPlayerIds?.takeIf { it.isNotEmpty() }?.let { return it }
        return stateStore.gameSnapshot(tableId)?.players.orEmpty().filter { it.isAi }.map { it.id }
    }
}
