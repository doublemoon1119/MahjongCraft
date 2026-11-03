package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterClientStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import net.minecraft.client.resource.language.I18n
import net.minecraft.text.Text
import kotlin.uuid.Uuid

/**
 * 集中解析所有 HUD、提示文字與世界面板使用的真人及 AI 顯示名稱。
 *
 * @property stateStore 提供房間與對局中的 AI 座位順序。
 * @property profiles 提供線上及已快取的真人 profile。
 * @property characters 已授權的目前與歷史角色描述。
 */
class ClientPlayerDisplayNameResolver(
    private val stateStore: ClientMahjongStateStore,
    private val profiles: ClientPlayerProfileResolver,
    private val characters: AiCharacterClientStateStore,
) {
    /**
     * 以房間持久化 AI 順序為優先，沒有房間資料時才退回目前遊戲快照順序。[tableId] 為 `null`（呼叫端一時
     * 拿不到管理中的桌子 ID）時，可用已授權成員索引解析角色名稱；AI 順序退回空清單。
     *
     * @param tableId 目前桌子的 UUID；null 時只查已授權角色索引，不查房間或對局快照。
     * @param playerId 欲解析名稱的玩家 UUID 字串。
     * @param isAiHint 呼叫端已知的 AI 身分；null 時由桌況推導。
     * @return AI 座位名稱、已快取真人名稱或未知玩家文字。
     */
    fun resolve(tableId: Uuid?, playerId: String, isAiHint: Boolean? = null): String {
        val id = runCatching { Uuid.parse(playerId) }.getOrNull() ?: return unknownName()
        val room = tableId?.let(stateStore::roomSnapshot)
        val character = if (tableId == null) characters.findPlayer(id) else characters.find(tableId)?.let { it.players[id] ?: it.excludedPlayers[id] }
        val isAi = isAiHint ?: (character != null || (tableId != null && (id in stateStore.gameAiPlayerIds(tableId) || id in room?.aiPlayerIds.orEmpty())))
        if (isAi) {
            val order = orderedAiPlayerIds(tableId)
            val appearances = order.mapNotNull { memberId ->
                val appearance = if (tableId == null) characters.findPlayer(memberId) else characters.find(tableId)?.players?.get(memberId)
                appearance?.let { memberId to it }
            }.toMap()
            return characterName(character) ?: aiPlayerDisplayName(id, order, appearances)
        }
        return profiles.knownProfile(id)?.name?.takeIf(String::isNotBlank) ?: unknownName()
    }

    /**
     * 只依指定歷史場次解析名稱，不借用目前世界角色。
     *
     * @param playerId 該場成員。
     * @param matchId 已授權的歷史場次。
     * @param isAi 該場是否由 AI 操控。
     * @param aiOrder 該場 AI 座位順序。
     * @return 當場角色名稱、AI 編號或真人已知名稱。
     */
    fun historical(playerId: Uuid, matchId: Uuid?, isAi: Boolean, aiOrder: List<Uuid>): String {
        if (isAi) {
            val appearances = aiOrder.mapNotNull { id -> matchId?.let { characters.history(it, id) }?.let { id to it } }.toMap()
            return characterName(matchId?.let { characters.history(it, playerId) }) ?: aiPlayerDisplayName(playerId, aiOrder, appearances)
        }
        return profiles.knownProfile(playerId)?.name?.takeIf(String::isNotBlank) ?: unknownName()
    }

    /**
     * 普通自訂名稱優先；其他角色使用本地翻譯，內建預設角色保留 AI 編號。
     *
     * @param snapshot 已授權的角色描述。
     * @return 名稱；缺少可顯示資料時由呼叫端使用 AI 編號。
     */
    private fun characterName(snapshot: AiCharacterSnapshot?): String? {
        snapshot?.displayName?.let { return it }
        if (snapshot?.characterId == BuiltInAiCharacters.TILE_MASCOT_ID) return null
        return snapshot?.definition?.nameKey?.takeIf(I18n::hasTranslation)?.let { Text.translatable(it).string }
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
