package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettlementKind
import com.doublemoon1119.mahjongcraft.flow.api.event.WinGroupSource
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver
import kotlin.uuid.Uuid

/** 依胡牌結算的台種（包含花胡）及終局事實判定台灣麻將專屬成果。 */
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

                else -> Unit
            }
        }
        return achievements
    }
}
