package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import net.minecraft.text.Text
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 集中解析所有 HUD、提示文字與世界面板使用的真人及 AI 顯示名稱。
 *
 * @property stateStore 提供房間與對局中的 AI 座位順序。
 * @property profiles 提供線上及已快取的真人 profile。
 */
@Single
class ClientPlayerDisplayNameResolver(
    private val stateStore: ClientMahjongStateStore,
    private val profiles: ClientPlayerProfileResolver,
) {
    /**
     * 以房間持久化 AI 順序為優先，沒有房間資料時才退回目前遊戲快照順序。[tableId] 為 `null`（呼叫端一時
     * 拿不到管理中的桌子 ID）時，AI 順序退回空清單、真人名稱解析不受影響。
     */
    fun resolve(tableId: Uuid?, playerId: String, isAiHint: Boolean? = null): String {
        val id = runCatching { Uuid.parse(playerId) }.getOrNull() ?: return unknownName()
        val snapshotPlayer = tableId?.let(stateStore::gameSnapshot)?.players?.firstOrNull { it.id == id }
        val isAi = isAiHint ?: snapshotPlayer?.isAi == true
        if (isAi) return aiPlayerDisplayName(id, orderedAiPlayerIds(tableId))
        return profiles.knownProfile(id)?.name?.takeIf(String::isNotBlank) ?: unknownName()
    }

    /** 不以 UUID 前綴冒充玩家名稱的本地化未知值。 */
    private fun unknownName(): String = Text.translatable(MinecraftHistoryScreenKeys.PLAYER_UNKNOWN).string

    private fun orderedAiPlayerIds(tableId: Uuid?): List<Uuid> {
        if (tableId == null) return emptyList()
        val lobbyIds = stateStore.tableOccupancy(tableId)?.playingAiPlayerIds.orEmpty()
            .mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
        if (lobbyIds.isNotEmpty()) return lobbyIds
        stateStore.roomSnapshot(tableId)?.aiPlayerIds?.takeIf { it.isNotEmpty() }?.let { return it }
        return stateStore.gameSnapshot(tableId)?.players.orEmpty().filter { it.isAi }.map { it.id }
    }
}
