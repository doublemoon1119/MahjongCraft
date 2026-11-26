package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 平台無關的統一流局結算呈現請求。
 *
 * @property reasonId 規則提供的完整流局原因 ID。
 * @property players 依固定座位順序排列的玩家結算資料。
 */
data class ExhaustiveDrawSettlementPresentationRequest(
    val reasonId: String,
    val players: List<ExhaustiveDrawSettlementPlayerPresentation>,
) {
    /** 流局專屬玩家資料投影出的共用分數排行呈現。 */
    val scoreRanking: ScoreRankingPresentation = ScoreRankingPresentation(players.map(ExhaustiveDrawSettlementPlayerPresentation::ranking))
}
