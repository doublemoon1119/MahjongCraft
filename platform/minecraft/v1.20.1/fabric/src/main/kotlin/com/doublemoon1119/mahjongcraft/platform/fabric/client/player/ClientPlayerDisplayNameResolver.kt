package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import net.minecraft.text.Text
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 集中解析所有 HUD、提示文字與世界面板使用的真人及 AI 顯示名稱。
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
     *
     * @param tableId 目前桌子的 UUID；null 時不查詢房間或對局快照。
     * @param playerId 欲解析名稱的玩家 UUID 字串。
     * @param isAiHint 呼叫端已知的 AI 身分；null 時由桌況推導。
     * @return AI 座位名稱、已快取真人名稱或未知玩家文字。
     */
    fun resolve(tableId: Uuid?, playerId: String, isAiHint: Boolean? = null): String {
        val id = runCatching { Uuid.parse(playerId) }.getOrNull() ?: return unknownName()
        val isAi = isAiHint ?: (tableId != null && id in stateStore.gameAiPlayerIds(tableId))
        if (isAi) return aiPlayerDisplayName(id, orderedAiPlayerIds(tableId))
        return profiles.knownProfile(id)?.name?.takeIf(String::isNotBlank) ?: unknownName()
    }

    /**
     * 不以 UUID 前綴冒充玩家名稱的本地化未知值。
     *
     * @return 本地化的未知玩家文字。
     */
    private fun unknownName(): String = Text.translatable(MinecraftHistoryScreenKeys.PLAYER_UNKNOWN).string

    /**
     * 依桌況資料優先順序取得房間內 AI 的座位排列。
     *
     * @param tableId 目前桌子的 UUID；null 時回傳空清單。
     * @return 目前可用的有序 AI UUID 清單。
     */
    private fun orderedAiPlayerIds(tableId: Uuid?): List<Uuid> {
        if (tableId == null) return emptyList()
        val lobbyIds = stateStore.tableOccupancy(tableId)?.playingAiPlayerIds.orEmpty()
            .mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
        if (lobbyIds.isNotEmpty()) return lobbyIds
        stateStore.roomSnapshot(tableId)?.aiPlayerIds?.takeIf { it.isNotEmpty() }?.let { return it }
        val aiPlayerIds = stateStore.gameAiPlayerIds(tableId)
        return stateStore.gameSnapshot(tableId)?.players.orEmpty().map { it.id }.filter { it in aiPlayerIds }
    }
}
