package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 在最後捨牌反應已完成、普通荒牌流局之前，判定並套用第一個成立的特殊 round outcome。
 *
 * @property gameRepository 權威對局狀態的交易來源。
 * @property moduleRegistry 目前對局規則模組的解析來源。
 * @property resolverRegistry 反應裁定後特殊結果的解析器。
 * @property snapshotSynchronizer 已提交結果的快照同步服務。
 * @property winSettlementDetailResolverRegistry 胡牌等價特殊結果的公開詳情解析器。
 */
@Factory
class ResolvePostReactionRoundOutcomeUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: PostReactionRoundOutcomeResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry,
) {
    /**
     * 原子判定並寫回特殊結果；沒有 resolver 成立時成功回傳 `null`，呼叫端應繼續普通流局。
     * @param gameId 欲推進的對局識別碼。
     * @return 已套用的特殊結果、無成立結果或對局不存在錯誤。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<ResolvedRoundOutcome?, GameError> {
        val result = gameRepository.updateGame(
            gameId,
            history = { previous, next, outcome ->
                val resolved = (outcome as? Outcome.Success)?.value
                if (previous == null || next == null || resolved == null) {
                    emptyList()
                } else {
                    val module = moduleRegistry.getModule(next.tableState.config)
                    val detailFields = if (resolved.presentationClassification == RoundOutcomePresentationClassification.WIN_EQUIVALENT) {
                        winSettlementDetailResolverRegistry.resolveSpecialOutcome(module.id, next.tableState, resolved)
                    } else {
                        null
                    }
                    listOf(
                        HistoryEventDraft(
                            actorPlayerId = null,
                            fact = HistoryFact.RuleEffectResolved(
                                reasonId = resolved.id,
                                roundCompletion = next.roundCompletion,
                                winDetails = detailFields?.let { fields ->
                                    resolved.beneficiaryPlayerIds.map { playerId -> HistoryWinDetails(playerId, fields) }
                                }.orEmpty(),
                            ),
                        ),
                    )
                }
            },
        ) { game ->
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
                        RoundOutcomePresentationClassification.WIN_EQUIVALENT ->
                            RoundCompletionClassification.WIN
                        RoundOutcomePresentationClassification.EXHAUSTIVE_DRAW_EQUIVALENT ->
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
