package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/** 在最後捨牌反應已完成、普通荒牌流局之前，判定並套用第一個成立的特殊 round outcome。 */
@Factory
class ResolvePostReactionRoundOutcomeUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: PostReactionRoundOutcomeResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
) {
    /**
     * 原子判定並寫回特殊結果；沒有 resolver 成立時成功回傳 `null`，呼叫端應繼續普通流局。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<ResolvedRoundOutcome?, GameError> {
        val result = gameRepository.updateGame(gameId) { game ->
            if (game == null) return@updateGame game to Outcome.Error(GameError.GameNotFound(gameId))
            val state = game.tableState
            val module = moduleRegistry.getModule(state.config)
            val resolved = resolverRegistry.resolve(state, module)
                ?: return@updateGame game to Outcome.Success(null)
            require(resolved.settledTableState.id == state.id) { "Resolved outcome must preserve the table id" }
            val actualDeltas = state.players.associate { previousPlayer ->
                val settledScore = resolved.settledTableState.players.first { it.id == previousPlayer.id }.score
                previousPlayer.id to (settledScore - previousPlayer.score)
            }
            require(resolved.scoreDeltas == actualDeltas) { "Resolved outcome score deltas do not match settled table state" }
            game.copy(
                tableState = resolved.settledTableState,
                roundCompletion = RoundCompletionSummary(
                    outcomeId = resolved.id,
                    classification = when (resolved.presentationClassification) {
                        com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification.WIN_EQUIVALENT ->
                            RoundCompletionClassification.WIN
                        com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification.EXHAUSTIVE_DRAW_EQUIVALENT ->
                            RoundCompletionClassification.EXTENSION
                    },
                    beneficiaryPlayerIds = resolved.beneficiaryPlayerIds,
                    responsiblePlayerIds = resolved.responsiblePlayerIds,
                    transitionDirective = resolved.transitionDirective,
                    settledScoresByPlayerId = resolved.settledTableState.players.associate { it.id to it.score },
                ),
            ) to Outcome.Success(resolved)
        }
        if (result is Outcome.Success && result.value != null) snapshotSynchronizer.syncAll(gameId)
        return result
    }
}
