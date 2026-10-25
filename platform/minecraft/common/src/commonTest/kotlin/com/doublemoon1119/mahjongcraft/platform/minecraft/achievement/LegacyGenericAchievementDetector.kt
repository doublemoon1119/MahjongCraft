package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import kotlin.uuid.Uuid

/** Frozen pre-event generic achievement detector used only for equivalence tests. */
internal object LegacyGenericAchievementDetector {
    fun detect(facts: CommittedGameFacts, module: MahjongRuleModule<*>): Map<Uuid, Set<String>> {
        val achievements = mutableMapOf<Uuid, MutableSet<String>>()
        fun add(playerId: Uuid, achievementId: String) {
            achievements.getOrPut(playerId) { linkedSetOf() } += achievementId
        }
        val acceptedTsumoPlayers = facts.facts.mapNotNull { draft ->
            (draft.fact as? HistoryFact.ActionAccepted)
                ?.takeIf { it.action is GameAction.Tsumo }
                ?.let { draft.actorPlayerId }
        }.toSet()
        val resolvedRon = facts.facts.any { draft ->
            val fact = draft.fact as? HistoryFact.ReactionResolved
            fact?.resolvedAction is GameAction.Ron
        }
        val winSettlements = facts.facts.mapNotNull { it.fact as? HistoryFact.WinSettled }
        val settledWinnerIds = winSettlements.flatMap { it.winDetails.map { details -> details.playerId } }.distinct()
        val hasBuiltInTsumo = acceptedTsumoPlayers.isEmpty() &&
            !resolvedRon &&
            winSettlements.any { it.outcomeId == BuiltInRoundOutcomeIds.TSUMO } &&
            winSettlements.none { it.outcomeId == BuiltInRoundOutcomeIds.RON }
        val hasBuiltInRon = acceptedTsumoPlayers.isEmpty() &&
            !resolvedRon &&
            winSettlements.any { it.outcomeId == BuiltInRoundOutcomeIds.RON } &&
            winSettlements.none { it.outcomeId == BuiltInRoundOutcomeIds.TSUMO }
        if (winSettlements.isNotEmpty()) {
            settledWinnerIds.forEach { winnerId -> add(winnerId, BuiltInAchievementIds.WIN) }
            val tsumoWinners = if (acceptedTsumoPlayers.isNotEmpty()) {
                acceptedTsumoPlayers.intersect(settledWinnerIds.toSet())
            } else if (hasBuiltInTsumo) {
                settledWinnerIds.toSet()
            } else {
                emptySet()
            }
            tsumoWinners.forEach { winnerId -> add(winnerId, BuiltInAchievementIds.SELF_DRAW_WIN) }
            val ronConfirmed = (resolvedRon && acceptedTsumoPlayers.isEmpty()) || hasBuiltInRon
            if (ronConfirmed) {
                winSettlements.groupBy { it.responsiblePlayerIds.toSet() }.forEach { (responsibleIds, settlements) ->
                    val groupWinnerIds = settlements.flatMap { it.winDetails.map { details -> details.playerId } }.distinct()
                    groupWinnerIds.forEach { winnerId -> add(winnerId, BuiltInAchievementIds.DISCARD_WIN) }
                    responsibleIds.forEach { dealerInId ->
                        add(dealerInId, BuiltInAchievementIds.DEAL_IN)
                        when (groupWinnerIds.size) {
                            2 -> add(dealerInId, BuiltInAchievementIds.DOUBLE_DEAL_IN)
                            3 -> add(dealerInId, BuiltInAchievementIds.TRIPLE_DEAL_IN)
                        }
                    }
                }
            }
        }
        facts.facts.forEach { draft ->
            when (val fact = draft.fact) {
                is HistoryFact.WinSettled -> Unit
                is HistoryFact.RuleEffectResolved -> {
                    val summary = fact.roundCompletion
                    if (summary?.classification == RoundCompletionClassification.WIN) {
                        val winnerIds = (fact.winDetails.map { it.playerId } + summary.beneficiaryPlayerIds).distinct()
                        winnerIds.forEach { winnerId -> add(winnerId, BuiltInAchievementIds.WIN) }
                    }
                }
                is HistoryFact.RoundCompleted -> if (fact.summary.classification == RoundCompletionClassification.EXHAUSTIVE_DRAW) {
                    fact.summary.settledScoresByPlayerId.keys.forEach { playerId -> add(playerId, BuiltInAchievementIds.EXHAUSTIVE_DRAW) }
                }
                is HistoryFact.MatchCompleted -> {
                    fact.finalScoresByPlayerId.keys.forEach { playerId -> add(playerId, BuiltInAchievementIds.MATCH_COMPLETED) }
                    val players = (facts.game ?: facts.previousGame)?.tableState?.players.orEmpty()
                    if (players.size > 1) {
                        val ranked = players.sortedWith(module.compareForMatchRanking())
                        add(ranked.first().id, BuiltInAchievementIds.FIRST_PLACE)
                        add(ranked.last().id, BuiltInAchievementIds.LAST_PLACE)
                    }
                }
                is HistoryFact.MatchAborted -> Unit
                else -> Unit
            }
        }
        return achievements
    }
}
