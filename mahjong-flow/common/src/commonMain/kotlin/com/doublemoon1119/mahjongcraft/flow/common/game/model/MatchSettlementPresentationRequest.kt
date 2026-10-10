package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 平台無關的終局結算呈現請求。
 *
 * @property ruleModuleId 本場使用的規則模組 ID；平台依此選擇呈現方式。
 * @property players 依固定座位順序保存的玩家終局快照。
 */
data class MatchSettlementPresentationRequest(
    val ruleModuleId: String,
    val players: List<MatchSettlementPlayerPresentation>,
) {
    init {
        require(players.isNotEmpty()) { "Match settlement must contain at least one player" }
        require(players.map(MatchSettlementPlayerPresentation::playerId).distinct().size == players.size) {
            "Match settlement player IDs must be unique"
        }
        require(players.map(MatchSettlementPlayerPresentation::finalRank).sorted() == (1..players.size).toList()) {
            "Final ranks must form a complete one-based sequence"
        }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
    }
}
