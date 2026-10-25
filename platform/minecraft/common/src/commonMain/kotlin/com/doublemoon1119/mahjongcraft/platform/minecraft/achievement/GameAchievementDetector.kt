package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.uuid.Uuid

/**
 * 合併同一事件批次的通用成果與規則專屬成果，只回傳真人玩家的結果。
 *
 * 通用判定只讀公開事件，暫留的規則判定由 [resolveRule] 同步讀取提交事實並複製結果。
 * AI 身分取自該筆批次的玩家快照，無需依賴先前的開局通知；判定與歷史記錄政策無關。
 *
 * @property moduleRegistry 依對局設定識別規則，分派暫留的規則專屬判定。
 * @property resolverRegistry 規則專屬成果判定。
 */
class GameAchievementDetector(
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: GameAchievementResolverRegistry,
) {
    /**
     * 判定 [facts] 中規則專屬的成果。
     *
     * @param facts 本次交易對單一桌子提交的事實。
     * @return 以玩家 UUID 索引的規則專屬成果。
     */
    fun resolveRule(facts: CommittedGameFacts): Map<Uuid, Set<String>> {
        val game = facts.game ?: checkNotNull(facts.previousGame)
        val module = moduleRegistry.getModule(game.tableState.config)
        val resolved = resolverRegistry.resolve(module.id, facts)
        return buildMap {
            resolved.forEach { (playerId, achievementIds) ->
                put(playerId, buildSet { addAll(achievementIds) })
            }
        }
    }

    /**
     * 合併批次中的通用成果與規則專屬成果。
     *
     * @param batch 同一筆權威提交產生的事件批次。
     * @param ruleAchievements 已由規則專屬 resolver 判定的成果。
     * @return 每位真人玩家在這筆交易中取得的成果。
     */
    fun detect(batch: CommittedMatchEventBatch, ruleAchievements: Map<Uuid, Set<String>> = emptyMap()): List<PlayerAchievements> {
        val generic = GenericAchievementDetector.detect(batch)
        return batch.players
            .filterNot { it.isAi }
            .mapNotNull { player ->
                val ids = generic[player.playerId].orEmpty() + ruleAchievements[player.playerId].orEmpty()
                if (ids.isEmpty()) null else PlayerAchievements(player.playerId, batch.matchId, batch.ruleModuleId, ids.toSet())
            }
    }
}
