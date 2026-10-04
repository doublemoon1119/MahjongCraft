package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * 把已提交的事實轉成真人玩家的成果。
 *
 * 通用成果在這裡判定，規則專屬成果交給 [resolverRegistry]；AI 玩家不產生成果。
 * 與歷史記錄政策無關：只要事實已提交就會判定。
 *
 * @property moduleRegistry 依對局設定取得規則，用來排名與分派規則專屬判定。
 * @property resolverRegistry 規則專屬成果判定。
 */
class GameAchievementDetector(
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: GameAchievementResolverRegistry,
) {
    /**
     * 判定 [facts] 中各真人玩家的成果。
     *
     * @param facts 本次交易對單一桌子提交的事實。
     * @return 每位有成果的真人玩家一組。
     */
    fun detect(facts: CommittedGameFacts): List<PlayerAchievements> {
        val game = facts.game ?: checkNotNull(facts.previousGame)
        val module = moduleRegistry.getModule(game.tableState.config)
        val generic = GenericAchievementDetector.detect(facts, module)
        val rule = resolverRegistry.resolve(module.id, facts)
        return game.tableState.players
            .filterNot { it.isAi }
            .mapNotNull { player ->
                val ids = generic[player.id].orEmpty() + rule[player.id].orEmpty()
                if (ids.isEmpty()) null else PlayerAchievements(player.id, facts.matchId, module.id, ids)
            }
    }
}
