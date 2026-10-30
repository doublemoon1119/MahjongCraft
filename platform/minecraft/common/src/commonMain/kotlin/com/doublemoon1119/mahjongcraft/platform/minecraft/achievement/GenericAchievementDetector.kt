package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundCompletedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundCompletionKind
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.WinMethod
import kotlin.uuid.Uuid

/** 從已提交事件批次判定各規則都適用的成果；不讀取規則專屬資料。 */
internal object GenericAchievementDetector {
    /**
     * 判定本次事件批次中各玩家達成的通用成果。
     *
     * @param batch 同一筆權威提交產生的事件批次。
     * @return 以玩家 UUID 索引的成果 ID；沒有成果的玩家不出現。
     */
    fun detect(batch: CommittedMatchEventBatch): Map<Uuid, Set<String>> {
        val achievements = mutableMapOf<Uuid, MutableSet<String>>()

        /** 將成果加入指定玩家的集合，同一成果只保留一次。 */
        fun add(playerId: Uuid, achievementId: String) {
            achievements.getOrPut(playerId) { linkedSetOf() } += achievementId
        }

        val discardGroups = mutableMapOf<Pair<Set<Uuid>, Set<Uuid>>, MutableSet<Uuid>>()
        batch.events.filterIsInstance<RoundSettledEvent>().forEach { settlement ->
            settlement.winGroups.forEach { group ->
                val winners = group.winners.map { it.playerId }.toSet()
                winners.forEach { add(it, BuiltInAchievementIds.WIN) }
                group.winners.filter { it.method == WinMethod.SELF_DRAW }
                    .forEach { add(it.playerId, BuiltInAchievementIds.SELF_DRAW_WIN) }
                val discardWinners = group.winners
                    .filter { it.method == WinMethod.DISCARD }
                    .map { it.playerId }
                discardWinners.forEach { add(it, BuiltInAchievementIds.DISCARD_WIN) }
                if (discardWinners.isNotEmpty() || group.discarderPlayerIds.isNotEmpty()) {
                    val key = group.responsiblePlayerIds to group.discarderPlayerIds
                    discardGroups.getOrPut(key) { linkedSetOf() } += discardWinners
                }
            }
        }
        discardGroups.forEach { (responsibility, winners) ->
            responsibility.second.forEach { discarder ->
                add(discarder, BuiltInAchievementIds.DEAL_IN)
                when (winners.size) {
                    2 -> add(discarder, BuiltInAchievementIds.DOUBLE_DEAL_IN)
                    3 -> add(discarder, BuiltInAchievementIds.TRIPLE_DEAL_IN)
                }
            }
        }
        batch.events.filterIsInstance<RoundCompletedEvent>().forEach { completed ->
            if (completed.classification == RoundCompletionKind.EXHAUSTIVE_DRAW) {
                completed.players.forEach { add(it.playerId, BuiltInAchievementIds.EXHAUSTIVE_DRAW) }
            }
        }
        batch.events.filterIsInstance<MatchEndedEvent>().filter { it.completion == MatchCompletion.COMPLETED }.forEach { ended ->
            ended.completedPlayerIds.forEach { add(it, BuiltInAchievementIds.MATCH_COMPLETED) }
            if (ended.standings.size > 1) {
                add(ended.standings.first().playerId, BuiltInAchievementIds.FIRST_PLACE)
                add(ended.standings.last().playerId, BuiltInAchievementIds.LAST_PLACE)
            }
        }
        return achievements
    }
}
