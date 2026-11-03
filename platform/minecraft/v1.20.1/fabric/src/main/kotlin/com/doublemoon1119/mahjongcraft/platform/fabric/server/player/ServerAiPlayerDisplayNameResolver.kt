package com.doublemoon1119.mahjongcraft.platform.fabric.server.player

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.usesNumberedAiName
import kotlin.uuid.Uuid

/**
 * 以房間固定順序及當場角色名稱建立伺服器提示與面板的 AI 名稱。
 *
 * @property charactersForMatch 呼叫時才讀取指定場次的公開角色快照，不在組裝服務時載入保存資料。
 */
class ServerAiPlayerDisplayNameResolver(
    private val charactersForMatch: (Uuid) -> Map<Uuid, AiCharacterSnapshot>,
) {
    /**
     * 具名角色保留自己的名稱，其他 AI 依開局前房間順序編號。
     *
     * @param game 名稱所屬對局。
     * @param playerId 需要名稱的 AI 成員。
     * @return 普通自訂名稱、AI 編號或短 UUID 備援；需由客戶端翻譯的角色回傳 null。
     */
    fun resolve(game: Game, playerId: Uuid): String? {
        val characters = charactersForMatch(game.matchId)
        val character = characters[playerId]
        if (character?.displayName == null && !character.usesNumberedAiName()) return null
        return aiPlayerDisplayName(
            playerId = playerId,
            orderedAiPlayerIds = game.roomPlayerIds.filter(game::isAi),
            characters = characters,
        )
    }
}
