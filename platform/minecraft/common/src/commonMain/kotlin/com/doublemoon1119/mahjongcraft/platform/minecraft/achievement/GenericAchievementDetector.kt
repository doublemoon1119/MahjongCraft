package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import kotlin.uuid.Uuid

/** 從已提交事實判定各規則都適用的成果；不讀取規則專屬資料。 */
internal object GenericAchievementDetector {
    /**
     * 判定本次交易中各玩家達成的通用成果。
     *
     * @param facts 本次交易對單一桌子提交的事實。
     * @param module 這場對局的規則，只用來依規則排名。
     * @return 以玩家 UUID 索引的成果 ID；沒有成果的玩家不出現。
     */
    fun detect(facts: CommittedGameFacts, module: MahjongRuleModule<*>): Map<Uuid, Set<String>> {
        val achievements = mutableMapOf<Uuid, MutableSet<String>>()

        /** 將成果加入指定玩家的集合，同一成果只保留一次。 */
        fun add(playerId: Uuid, achievementId: String) {
            achievements.getOrPut(playerId) { linkedSetOf() } += achievementId
        }

        /** 同筆交易中明確提交自摸動作的玩家；只用於細分自摸成果。 */
        val acceptedTsumoPlayers = facts.facts.mapNotNull { draft ->
            (draft.fact as? HistoryFact.ActionAccepted)
                ?.takeIf { it.action is GameAction.Tsumo }
                ?.let { draft.actorPlayerId }
        }.toSet()

        /** 同筆交易是否有明確的榮和裁定；不套用到其他類型的規則效果。 */
        val resolvedRon = facts.facts.any { draft ->
            val fact = draft.fact as? HistoryFact.ReactionResolved
            fact?.resolvedAction is GameAction.Ron
        }

        /** 同筆交易中的所有和牌結算，合併後再計算多響成果，避免重複或漏算。 */
        val winSettlements = facts.facts.mapNotNull { it.fact as? HistoryFact.WinSettled }

        /** 合併同筆交易內所有和牌詳情後的去重贏家。 */
        val settledWinnerIds = winSettlements.flatMap { it.winDetails.map { details -> details.playerId } }.distinct()

        /** 沒有動作事實時，只有完整且互斥的內建結果 ID 才能分類為自摸。 */
        val hasBuiltInTsumo = acceptedTsumoPlayers.isEmpty() &&
            !resolvedRon &&
            winSettlements.any { it.outcomeId == BuiltInRoundOutcomeIds.TSUMO } &&
            winSettlements.none { it.outcomeId == BuiltInRoundOutcomeIds.RON }

        /** 沒有動作事實時，只有完整且互斥的內建結果 ID 才能分類為榮和。 */
        val hasBuiltInRon = acceptedTsumoPlayers.isEmpty() &&
            !resolvedRon &&
            winSettlements.any { it.outcomeId == BuiltInRoundOutcomeIds.RON } &&
            winSettlements.none { it.outcomeId == BuiltInRoundOutcomeIds.TSUMO }
        if (winSettlements.isNotEmpty()) {
            settledWinnerIds.forEach { winnerId -> add(winnerId, BuiltInAchievementIds.WIN) }
            /** 依已成立自摸動作或內建結果 ID 判定的贏家。 */
            val tsumoWinners = if (acceptedTsumoPlayers.isNotEmpty()) {
                acceptedTsumoPlayers.intersect(settledWinnerIds.toSet())
            } else if (hasBuiltInTsumo) {
                settledWinnerIds.toSet()
            } else {
                emptySet()
            }
            tsumoWinners.forEach { winnerId -> add(winnerId, BuiltInAchievementIds.SELF_DRAW_WIN) }
            /** 只有明確榮和裁定或內建結果 ID 才能產生放銃相關成果。 */
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
                    /** 規則效果提供的本局結算摘要；只有 WIN 分類能產生通用胡牌成果。 */
                    val summary = fact.roundCompletion
                    if (summary?.classification == RoundCompletionClassification.WIN) {
                        /** 此規則效果明確提供的贏家，供通用 WIN 成果使用。 */
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
