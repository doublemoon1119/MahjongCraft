package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import kotlin.uuid.Uuid

/**
 * 一張已公開、尚待規則決定後續處理的花牌。
 *
 * @property playerId 公開花牌的玩家。
 * @property tileId 已移到公開花區的牌張識別碼。
 * @property replacementsDue 這位玩家已公開、尚未補的張數；已補完時為 0。
 * @property resumeRound 繼續補花時所在的輪次，從 1 開始；這位玩家已補完時是下一位玩家所在的輪次。
 */
data class TaiwanPendingFlowerFollowUp(
    val playerId: Uuid,
    val tileId: Uuid,
    val replacementsDue: Int,
    val resumeRound: Int,
) {
    init {
        require(replacementsDue >= 0) { "Replacements due must not be negative" }
        require(resumeRound >= 1) { "Replacement round must start at one" }
    }
}
