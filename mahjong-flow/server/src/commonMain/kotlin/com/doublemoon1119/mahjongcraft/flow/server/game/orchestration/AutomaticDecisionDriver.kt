package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.AutomaticDecisionEvaluator
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/** 將真人玩家啟用的本局自動操作轉換成下一個可執行命令。 */
@Factory
class AutomaticDecisionDriver(
    private val gameRepository: GameRepository,
    private val evaluator: AutomaticDecisionEvaluator,
    private val commandMapper: GameActionCommandMapper,
    private val actionContextResolver: PlayerActionContextResolver = PlayerActionContextResolver(),
) {
    /** 解析下一個可立即執行的真人自動決策；目前沒有時回傳 null。 */
    suspend fun resolveNextAction(gameId: Uuid): Pair<Uuid, GameCommand>? {
        val game = gameRepository.getGame(gameId) ?: return null
        if (game.isMatchOver || game.pendingTransition != null || game.pendingRoundPreparation != null) return null
        val state = game.tableState
        val contexts = actionContextResolver.resolve(state)
        for ((playerId, context) in contexts) {
            val player = state.players.first { it.id == playerId }
            if (player.isAi || playerId in game.forcedAutoPlayPlayerIds) continue
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
