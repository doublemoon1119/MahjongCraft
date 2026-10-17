package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.accepts
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.RoundPreparationPresentationUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.uuid.Uuid

/**
 * 驗證並提交一位玩家的開局準備選擇。
 *
 * @property gameRepository 權威對局資料倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心。
 * @property resolverRegistry 開局準備步驟解析器註冊中心。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property presentationPublisher 更新準備結果改變的牌區與局況。
 */
class SubmitRoundPreparationUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RoundPreparationResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 對指定對局提交一次準備選擇。
     *
     * @param gameId 對局識別碼。
     * @param playerId 提交選擇的玩家識別碼。
     * @param submission 玩家提交的準備選擇。
     * @return 提交結果；對局不存在、準備步驟不可用或內容無效時回傳 [GameError]。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        submission: RoundPreparationSubmission,
    ): Outcome<Unit, GameError> {
        val result = gameRepository.updateGame(
            gameId,
            history = { _, _, update -> update.historyDraft?.let(::listOf).orEmpty() },
        ) { game ->
            if (game == null) {
                return@updateGame null to PreparationSubmissionUpdate(
                    Outcome.Error(GameError.GameNotFound(gameId)),
                    null,
                )
            }
            val preparation = game.pendingRoundPreparation
                ?: return@updateGame game to PreparationSubmissionUpdate(
                    Outcome.Error(GameError.RoundPreparationUnavailable(gameId, playerId)),
                    null,
                )
            val input = preparation.inputSpecsByPlayerId[playerId]
                ?: return@updateGame game to PreparationSubmissionUpdate(
                    Outcome.Error(GameError.RoundPreparationUnavailable(gameId, playerId)),
                    null,
                )
            if (playerId in preparation.completedPlayerIds || !input.accepts(submission)) {
                return@updateGame game to PreparationSubmissionUpdate(
                    Outcome.Error(GameError.InvalidRoundPreparationSubmission(gameId, playerId)),
                    null,
                )
            }
            val module = moduleRegistry.getModule(game.tableState.config)
            val resolver = resolverRegistry.find(module.id)
                ?: return@updateGame game to PreparationSubmissionUpdate(
                    Outcome.Error(GameError.InvalidRoundPreparationSubmission(gameId, playerId)),
                    null,
                )
            if (!resolver.accepts(game.tableState, preparation, playerId, submission, module)) {
                return@updateGame game to PreparationSubmissionUpdate(
                    Outcome.Error(GameError.InvalidRoundPreparationSubmission(gameId, playerId)),
                    null,
                )
            }
            val submitted = preparation.copy(
                submissionsByPlayerId = preparation.submissionsByPlayerId + (playerId to submission),
            )
            var updated = game.copy(pendingRoundPreparation = submitted)
            if (submitted.isComplete) {
                val resolution = resolver.resolve(game.tableState, submitted, module)
                updated = game.copy(tableState = resolution.tableState, pendingRoundPreparation = resolution.nextStep)
            }
            updated to PreparationSubmissionUpdate(
                Outcome.Success(Unit),
                HistoryEventDraft(
                    actorPlayerId = playerId,
                    fact = HistoryFact.RoundPreparationSubmitted(
                        stepId = preparation.stepId,
                        stepIndex = preparation.stepIndex,
                        submission = submission,
                        nextStepId = updated.pendingRoundPreparation?.stepId,
                    ),
                ),
                presentationUpdate = if (submitted.isComplete) {
                    RoundPreparationPresentationUpdate(game.tableState, updated.tableState)
                } else {
                    null
                },
            )
        }
        val outcome = result.outcome
        if (outcome is Outcome.Success) {
            result.presentationUpdate?.let { update ->
                update.publish(presentationPublisher, moduleRegistry.getModule(update.current.config))
            }
            snapshotSynchronizer.syncAll(gameId)
        }
        return outcome
    }

    /**
     * 將更新結果與可選的歷史事件草稿帶出交易區塊。
     *
     * @property outcome 對外回傳的提交結果。
     * @property historyDraft 本次成功提交的歷史事件；失敗時為 null。
     * @property presentationUpdate 已完成步驟的桌況差異；尚未收齊或提交失敗時為 null。
     */
    private data class PreparationSubmissionUpdate(
        val outcome: Outcome<Unit, GameError>,
        val historyDraft: HistoryEventDraft?,
        val presentationUpdate: RoundPreparationPresentationUpdate? = null,
    )
}
