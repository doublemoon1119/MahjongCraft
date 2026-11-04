package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionWinOutcome
import kotlin.uuid.Uuid

/**
 * 將規則選擇流程成立的和牌結果轉成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun RuleDecisionWinOutcome.toPersistenceDto(): RuleDecisionWinOutcomePersistenceDto = RuleDecisionWinOutcomePersistenceDto(
    outcomeId = outcomeId,
    winnerPlayerIds = winnerPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    responsiblePlayerIds = responsiblePlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    winningTileId = winningTileId.toString(),
)

/**
 * 將 persistence DTO 還原成規則選擇流程成立的和牌結果。
 *
 * @return 轉換後的結果。
 */
fun RuleDecisionWinOutcomePersistenceDto.toDomain(): RuleDecisionWinOutcome = RuleDecisionWinOutcome(
    outcomeId = outcomeId,
    winnerPlayerIds = winnerPlayerIds.mapTo(mutableSetOf(), Uuid::parse),
    responsiblePlayerIds = responsiblePlayerIds.mapTo(mutableSetOf(), Uuid::parse),
    winningTileId = Uuid.parse(winningTileId),
)
