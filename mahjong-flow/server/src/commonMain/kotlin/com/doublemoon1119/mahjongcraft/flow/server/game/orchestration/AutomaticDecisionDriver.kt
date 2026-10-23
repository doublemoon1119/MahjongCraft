package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.AutomaticDecisionEvaluator
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import kotlin.uuid.Uuid

/**
 * 將真人玩家啟用的本局自動操作轉換成下一個可執行命令。
 *
 * @property gameRepository 讀取權威遊戲的資料倉庫。
 * @property evaluator 依玩家啟用的自動操作政策找出可立即執行的動作。
 * @property commandMapper 將動作與選牌轉換成命令。
 * @property actionContextResolver 解析玩家目前可操作的情境。
 */
class AutomaticDecisionDriver(
    private val gameRepository: GameRepository,
    private val evaluator: AutomaticDecisionEvaluator,
    private val commandMapper: GameActionCommandMapper,
    private val actionContextResolver: PlayerActionContextResolver = PlayerActionContextResolver(),
) {
    /**
     * 解析下一個可立即執行的真人自動決策。
     *
     * @param gameId 要查詢的遊戲識別碼。
     * @return 玩家識別碼與命令；遊戲不存在、已結束、尚有待完成流程，或沒有可立即執行的動作時為 null。
     */
    suspend fun resolveNextAction(gameId: Uuid): Pair<Uuid, GameCommand>? {
        val game = gameRepository.getGame(gameId) ?: return null
        if (game.isMatchOver || game.pendingTransition != null || game.pendingRuleDecision != null) return null
        val state = game.tableState
        val contexts = actionContextResolver.resolve(state)
        for ((playerId, context) in contexts) {
            if (game.isAi(playerId) || playerId in game.forcedAutoPlayPlayerIds) continue
            if (game.enabledAutomaticControlIdsByPlayerId[playerId].isNullOrEmpty()) continue
            val immediate = evaluator.evaluate(game, playerId)?.immediateAction ?: continue
            val command = requireNotNull(
                commandMapper.toCommand(context, immediate.action, immediate.selectedTileIds),
            ) { "Automatic control policy produced an action that cannot be mapped to a game command" }
            return playerId to command
        }
        return null
    }
}
