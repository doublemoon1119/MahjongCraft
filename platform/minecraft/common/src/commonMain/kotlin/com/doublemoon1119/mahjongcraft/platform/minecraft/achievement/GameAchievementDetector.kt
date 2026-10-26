package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch

/**
 * 合併同一事件批次的通用成果與規則專屬成果，只回傳真人玩家的結果。
 *
 * 通用判定與規則專屬判定都只讀公開事件；兩者結果在一次交易內合併。
 * AI 身分取自該筆批次的玩家快照，無需依賴先前的開局通知；判定與歷史記錄政策無關。
 *
 * @property resolverRegistry 規則專屬成果判定。
 */
class GameAchievementDetector(
    private val resolverRegistry: GameAchievementResolverRegistry,
) {
    /**
     * 合併批次中的通用成果與規則專屬成果。
     *
     * @param batch 同一筆權威提交產生的事件批次。
     * @return 每位真人玩家在這筆交易中取得的成果。
     */
    fun detect(batch: CommittedMatchEventBatch): List<PlayerAchievements> {
        val generic = GenericAchievementDetector.detect(batch)
        val rule = resolverRegistry.resolve(batch.ruleModuleId, batch)
        return batch.players
            .filterNot { it.isAi }
            .mapNotNull { player ->
                val ids = generic[player.playerId].orEmpty() + rule[player.playerId].orEmpty()
                if (ids.isEmpty()) null else PlayerAchievements(player.playerId, batch.matchId, batch.ruleModuleId, ids.toSet())
            }
    }
}
