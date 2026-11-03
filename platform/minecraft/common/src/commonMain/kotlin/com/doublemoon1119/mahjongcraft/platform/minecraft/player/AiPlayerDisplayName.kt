package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import kotlin.uuid.Uuid

/**
 * 依名冊順序為沒有自訂名稱的 AI 編號；具名角色不占用序號。
 *
 * 普通自訂名稱直接回傳，第三方角色的翻譯名稱由呈現端解析。成員移出等待房間後，編號依剩餘名冊重新排列。
 *
 * @param playerId 要顯示名稱的 AI 成員 UUID。
 * @param orderedAiPlayerIds 依房間或歷史固定順序排列的 AI 成員 UUID。
 * @param characters 同一房間或歷史場次的公開角色描述，不借用其他場次的名稱。
 * @return 普通自訂名稱或 AI 序號；沒有可用序號時使用 UUID 前六字元。
 */
fun aiPlayerDisplayName(
    playerId: Uuid,
    orderedAiPlayerIds: List<Uuid>,
    characters: Map<Uuid, AiCharacterSnapshot> = emptyMap(),
): String {
    characters[playerId]?.displayName?.let { return it }
    val numberedPlayers = orderedAiPlayerIds.filter { id ->
        val character = characters[id]
        character?.displayName == null &&
            (character?.definition == null || character.characterId == BuiltInAiCharacters.TILE_MASCOT_ID)
    }
    val ordinal = numberedPlayers.indexOf(playerId)
    return if (ordinal >= 0) "AI ${ordinal + 1}" else "AI-${playerId.toString().take(6)}"
}
