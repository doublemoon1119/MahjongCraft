package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import kotlin.uuid.Uuid

/** 內建通用終局結算模板 key。 */
val BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY: String = MahjongCraftMetadata.id("generic_match_settlement")

/**
 * 單一玩家的權威終局排行快照。
 *
 * @property playerId 玩家 Uuid。
 * @property seatIndex 對局開始時的固定座位 index。
 * @property isAi 是否由 AI 操控。
 * @property initialSeat 起家座位，供規則同分順位與呈現 fallback 使用。
 * @property finalScore 收取剩餘供託後的最終桌上點數。
 * @property finalRank 規則已判定的最終名次，從 1 開始。
 */
data class MatchSettlementPlayerPresentation(
    val playerId: Uuid,
    val seatIndex: Int,
    val isAi: Boolean,
    val initialSeat: Wind,
    val finalScore: Int,
    val finalRank: Int,
)

/**
 * 平台無關的終局結算呈現請求。
 *
 * @property players 依固定座位順序保存的玩家終局快照。
 * @property templateKey 完整 namespaced 宣告式模板 key。
 */
data class MatchSettlementPresentationRequest(
    val players: List<MatchSettlementPlayerPresentation>,
    val templateKey: String = BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY,
) {
    init {
        require(players.isNotEmpty()) { "Match settlement must contain at least one player" }
        require(players.map(MatchSettlementPlayerPresentation::playerId).distinct().size == players.size) {
            "Match settlement player IDs must be unique"
        }
        require(players.map(MatchSettlementPlayerPresentation::finalRank).sorted() == (1..players.size).toList()) {
            "Final ranks must form a complete one-based sequence"
        }
        require(templateKey.isNotBlank()) { "Match settlement template key must not be blank" }
    }
}
