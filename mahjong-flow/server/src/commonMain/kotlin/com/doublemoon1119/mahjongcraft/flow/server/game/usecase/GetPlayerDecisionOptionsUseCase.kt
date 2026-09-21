package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionOptions
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.AutomaticDecisionEvaluator
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/** 查詢指定玩家目前完整決策選項的應用層用例。 */
@Factory
class GetPlayerDecisionOptionsUseCase(
    private val gameRepository: GameRepository,
    private val automaticDecisionEvaluator: AutomaticDecisionEvaluator,
) {
    /**
     * 查詢合法動作、選牌需求、捨牌分析與決策觸發牌。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 欲查詢的玩家 Uuid。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid): Outcome<PlayerDecisionOptions, GameError> {
        val game = gameRepository.getGame(gameId)
            ?: return Outcome.Error(GameError.GameNotFound(gameId))
        val player = game.tableState.players.firstOrNull { it.id == playerId }
            ?: return Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
        return Outcome.Success(
            requireNotNull(automaticDecisionEvaluator.evaluate(game, player.id)).options,
        )
    }
}
