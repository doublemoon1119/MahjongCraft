package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionWinOutcome
import kotlinx.serialization.Serializable

/**
 * [RuleDecisionWinOutcome] 的 persistence DTO。
 *
 * @property outcomeId 完整 namespaced 結果 ID。
 * @property winnerPlayerIds 和牌玩家 UUID 字串集合。
 * @property responsiblePlayerIds 責任玩家 UUID 字串集合。
 * @property winningTileId 和牌成立所依據的牌張 UUID 字串。
 */
@Serializable
data class RuleDecisionWinOutcomePersistenceDto(
    val outcomeId: String,
    val winnerPlayerIds: Set<String>,
    val responsiblePlayerIds: Set<String>,
    val winningTileId: String,
)
