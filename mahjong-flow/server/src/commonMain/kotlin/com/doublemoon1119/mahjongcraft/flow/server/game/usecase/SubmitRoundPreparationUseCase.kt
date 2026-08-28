package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.accepts
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/** 驗證並提交一位玩家的開局準備選擇。 */
@Factory
class SubmitRoundPreparationUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RoundPreparationResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
) {
    /** 對指定對局提交一次準備選擇。 */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        submission: RoundPreparationSubmission,
    ): Outcome<Unit, GameError> {
        val result = gameRepository.updateGame(gameId) { game ->
            if (game == null) return@updateGame null to Outcome.Error(GameError.GameNotFound(gameId))
            val preparation = game.pendingRoundPreparation
                ?: return@updateGame game to Outcome.Error(GameError.RoundPreparationUnavailable(gameId, playerId))
            val input = preparation.inputSpecsByPlayerId[playerId]
                ?: return@updateGame game to Outcome.Error(GameError.RoundPreparationUnavailable(gameId, playerId))
            if (playerId in preparation.completedPlayerIds || !input.accepts(submission)) {
                return@updateGame game to Outcome.Error(GameError.InvalidRoundPreparationSubmission(gameId, playerId))
            }
            val module = moduleRegistry.getModule(game.tableState.config)
            val resolver = resolverRegistry.find(module.id)
                ?: return@updateGame game to Outcome.Error(GameError.InvalidRoundPreparationSubmission(gameId, playerId))
            if (!resolver.accepts(game.tableState, preparation, playerId, submission, module)) {
                return@updateGame game to Outcome.Error(GameError.InvalidRoundPreparationSubmission(gameId, playerId))
            }
            val submitted = preparation.copy(
                submissionsByPlayerId = preparation.submissionsByPlayerId + (playerId to submission),
            )
            var updated = game.copy(pendingRoundPreparation = submitted)
            if (submitted.isComplete) {
                val resolution = resolver.resolve(game.tableState, submitted, module)
                updated = game.copy(tableState = resolution.tableState, pendingRoundPreparation = resolution.nextStep)
            }
            updated to Outcome.Success(Unit)
        }
        if (result is Outcome.Success) snapshotSynchronizer.syncAll(gameId)
        return result
    }
}
