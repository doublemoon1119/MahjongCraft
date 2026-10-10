package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
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
        fun add(playerId: Uuid, achievementId: String) {
            achievements.getOrPut(playerId) { linkedSetOf() } += achievementId
        }
        facts.facts.forEach { draft ->
            when (val fact = draft.fact) {
                is HistoryFact.WinSettled -> {
                    val winnerIds = fact.winDetails.map { it.playerId }.distinct()
                    when (fact.outcomeId) {
                        BuiltInRoundOutcomeIds.TSUMO -> winnerIds.forEach { winnerId ->
                            add(winnerId, BuiltInAchievementIds.WIN)
                            add(winnerId, BuiltInAchievementIds.SELF_DRAW_WIN)
                        }
                        BuiltInRoundOutcomeIds.RON -> {
                            winnerIds.forEach { winnerId ->
                                add(winnerId, BuiltInAchievementIds.WIN)
                                add(winnerId, BuiltInAchievementIds.DISCARD_WIN)
                            }
                            fact.responsiblePlayerIds.forEach { dealerInId ->
                                add(dealerInId, BuiltInAchievementIds.DEAL_IN)
                                when (winnerIds.size) {
                                    2 -> add(dealerInId, BuiltInAchievementIds.DOUBLE_DEAL_IN)
                                    3 -> add(dealerInId, BuiltInAchievementIds.TRIPLE_DEAL_IN)
                                }
                            }
                        }
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
