package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/** 在呈現層閒置後，收斂不需要玩家輸入的開局準備步驟。 */
@Factory
class AdvanceAutomaticRoundPreparationUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RoundPreparationResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
) {
    /**
     * 收斂目前連續的自動步驟。
     *
     * @param gameId 對局 Uuid。
     * @return 權威 preparation 或桌況是否實際改變。
     */
    suspend operator fun invoke(gameId: Uuid): Boolean {
        val update = gameRepository.updateGame(
            gameId,
            history = { _, _, result -> result.historyDrafts },
        ) { game ->
            val currentGame = game
                ?: return@updateGame null to AutomaticPreparationUpdate(emptyList())
            val preparation = currentGame.pendingRoundPreparation
                ?: return@updateGame currentGame to AutomaticPreparationUpdate(emptyList())
            if (preparation.participantPlayerIds.isNotEmpty()) {
                return@updateGame currentGame to AutomaticPreparationUpdate(emptyList())
            }
            val module = moduleRegistry.getModule(currentGame.tableState.config)
            val resolver = resolverRegistry.find(module.id)
                ?: return@updateGame currentGame to AutomaticPreparationUpdate(emptyList())
            var updated = currentGame
            val drafts = buildList {
                repeat(MAX_AUTOMATIC_PREPARATION_STEPS) {
                    val currentPreparation = updated.pendingRoundPreparation
                        ?: return@repeat
                    if (currentPreparation.participantPlayerIds.isNotEmpty()) return@repeat
                    val resolution = resolver.resolve(updated.tableState, currentPreparation, module)
                    updated = updated.copy(
                        tableState = resolution.tableState,
                        pendingRoundPreparation = resolution.nextStep,
                    )
                    add(
                        HistoryEventDraft(
                            actorPlayerId = null,
                            fact = HistoryFact.RoundPreparationAutomaticallyResolved(
                                stepId = currentPreparation.stepId,
                                stepIndex = currentPreparation.stepIndex,
                                resultingTableState = resolution.tableState,
                                nextStepId = resolution.nextStep?.stepId,
                            ),
                        ),
                    )
                }
            }
            if (updated.pendingRoundPreparation?.participantPlayerIds?.isEmpty() == true) {
                error("Round preparation did not converge after $MAX_AUTOMATIC_PREPARATION_STEPS automatic steps")
            }
            updated to AutomaticPreparationUpdate(drafts)
        }
        if (update.changed) snapshotSynchronizer.syncAll(gameId)
        return update.changed
    }

    private data class AutomaticPreparationUpdate(
        val historyDrafts: List<HistoryEventDraft>,
    ) {
        val changed: Boolean get() = historyDrafts.isNotEmpty()
    }

    private companion object {
        const val MAX_AUTOMATIC_PREPARATION_STEPS: Int = 128
    }
}
