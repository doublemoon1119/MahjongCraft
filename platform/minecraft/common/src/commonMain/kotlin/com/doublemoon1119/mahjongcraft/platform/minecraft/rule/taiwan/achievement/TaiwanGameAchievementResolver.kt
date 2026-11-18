package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettlementKind
import com.doublemoon1119.mahjongcraft.flow.api.event.RuleResultsResolvedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.WinGroupSource
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver
import kotlin.uuid.Uuid

/** 依正式結算及特殊花牌結果判定台灣麻將專屬成果。 */
internal class TaiwanGameAchievementResolver : GameAchievementResolver {
    override val ruleModuleId: String = BuiltInRuleModuleIds.TAIWAN

    override fun resolve(batch: CommittedMatchEventBatch): Map<Uuid, Set<String>> {
        val achievements = mutableMapOf<Uuid, MutableSet<String>>()
        fun add(playerId: Uuid, id: String) {
            achievements.getOrPut(playerId) { linkedSetOf() }.add(id)
        }

        batch.events.forEach { event ->
            when (event) {
                is MatchEndedEvent -> if (event.completion == MatchCompletion.COMPLETED) {
                    event.completedPlayerIds.forEach { add(it, TaiwanAchievementIds.MATCH_COMPLETED) }
                }

                is RoundSettledEvent -> if (event.kind == RoundSettlementKind.WIN) {
                    event.winGroups.filter { it.source == WinGroupSource.WIN_SETTLEMENT }.forEach { group ->
                        group.winners.forEach { winner ->
                            winner.detailFields.filter { it.id == TaiwanWinSettlementIds.TAI_FIELD }
                                .flatMap { it.entries }
                                .mapNotNull { TaiwanWinSettlementIds.taiType(it.id) }
                                .mapNotNull(TaiwanAchievementIds::forTai)
                                .forEach { add(winner.playerId, it) }
                        }
                    }
                }

                is RuleResultsResolvedEvent -> event.results.forEach resultLoop@{ result ->
                    val id = when (result.reasonId) {
                        TaiwanRoundOutcomeIds.EIGHT_FLOWERS -> TaiwanAchievementIds.EIGHT_FLOWERS
                        TaiwanRoundOutcomeIds.SEVEN_FLOWERS -> TaiwanAchievementIds.SEVEN_FLOWERS
                        else -> return@resultLoop
                    }
                    val winners = result.winDetails.map { it.playerId }
                        .ifEmpty { result.completion?.beneficiaryPlayerIds.orEmpty().toList() }
                    winners.forEach { add(it, id) }
                }

                else -> Unit
            }
        }
        return achievements
    }
}
