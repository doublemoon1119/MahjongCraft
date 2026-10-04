package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 一位真人玩家在同一次權威交易中達成的成果。
 *
 * 同一次交易的成果放在同一組，讓進度能要求「同一次和牌同時成立多個成果」。
 *
 * @property playerId 達成成果的玩家。
 * @property matchId 成果所屬的場次。
 * @property ruleModuleId 這場對局使用的完整規則模組 ID。
 * @property achievementIds 本次達成的完整 namespaced 成果 ID，至少一個。
 */
data class PlayerAchievements(
    val playerId: Uuid,
    val matchId: Uuid,
    val ruleModuleId: String,
    val achievementIds: Set<String>,
) {
    init {
        require(achievementIds.isNotEmpty()) { "Player achievements must not be empty" }
        achievementIds.forEach { id -> NamespacedId.requireValid(id) { "Achievement ID must be namespaced: $id" } }
    }
}
