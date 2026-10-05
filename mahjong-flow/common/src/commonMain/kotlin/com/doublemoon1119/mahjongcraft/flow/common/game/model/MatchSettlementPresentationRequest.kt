package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 單一玩家的權威終局排行快照。
 *
 * @property playerId 玩家 Uuid。
 * @property seatIndex 對局開始時的固定座位 index。
 * @property isAi 是否由 AI 操控。
 * @property initialSeatIndex 整場固定起家順位，供規則同分順位與呈現 fallback 使用。
 * @property finalScore 收取剩餘供託後的最終桌上點數。
 * @property finalRank 規則已判定的最終名次，從 1 開始。
 */
data class MatchSettlementPlayerPresentation(
    val playerId: Uuid,
    val seatIndex: Int,
    val isAi: Boolean,
    val initialSeatIndex: Int,
    val finalScore: Int,
    val finalRank: Int,
)

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
