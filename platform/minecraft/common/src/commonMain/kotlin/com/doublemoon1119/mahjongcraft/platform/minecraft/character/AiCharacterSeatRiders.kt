package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlin.uuid.Uuid

/** 以完整席位名冊建立乘坐者列表，恢復代理時不取得真人。 */
object AiCharacterSeatRiders {
    /**
     * 解析同一份名冊，不過濾空位或改變索引。
     *
     * @param R 平台乘坐者型別。
     * @param roster 完整席位名冊。
     * @param includePlayers 開局及明確換位可移動真人；一般恢復須為 false。
     * @param player 只查詢在線真人。
     * @param character 只查詢本場有效的 AI 角色。
     * @return 與原名冊等長的乘坐者，無法解析的席位為 null。
     */
    fun <R> resolve(
        roster: List<Uuid>,
        includePlayers: Boolean,
        player: (Uuid) -> R?,
        character: (Uuid) -> R?,
    ): List<R?> = roster.map { playerId ->
        if (includePlayers) player(playerId) ?: character(playerId) else character(playerId)
    }
}
