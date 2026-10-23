package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.accepts
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.RuleDecisionPresentationUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.uuid.Uuid

/**
 * 驗證並提交一位玩家的規則選擇。
 *
 * @property gameRepository 權威對局資料倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心。
 * @property resolverRegistry 規則選擇步驟解析器註冊中心。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property presentationPublisher 更新規則選擇結果改變的牌區與局況。
 */
class SubmitRuleDecisionUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RuleDecisionResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 對指定對局提交一次規則選擇。
     *
     * @param gameId 對局識別碼。
     * @param playerId 提交選擇的玩家識別碼。
     * @param windowId 提交對象的選擇視窗；不是目前開著的視窗時視為過期。
     * @param submission 玩家提交的規則選擇。
     * @return 提交結果；對局不存在、視窗已過期、選擇步驟不可用或內容無效時回傳 [GameError]。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        windowId: Uuid,
        submission: RuleDecisionSubmission,
    ): Outcome<Unit, GameError> {
        val result = gameRepository.updateGame(
            gameId,
            history = { _, _, update -> update.historyDraft?.let(::listOf).orEmpty() },
        ) { game ->
            if (game == null) {
                return@updateGame null to RuleDecisionSubmissionUpdate(
                    Outcome.Error(GameError.GameNotFound(gameId)),
                    null,
                )
            }
            val ruleDecision = game.pendingRuleDecision?.takeIf { it.id == windowId }
                ?: return@updateGame game to RuleDecisionSubmissionUpdate(
                    Outcome.Error(GameError.RuleDecisionUnavailable(gameId, playerId)),
                    null,
                )
            val input = ruleDecision.inputSpecsByPlayerId[playerId]
                ?: return@updateGame game to RuleDecisionSubmissionUpdate(
                    Outcome.Error(GameError.RuleDecisionUnavailable(gameId, playerId)),
                    null,
                )
            if (playerId in ruleDecision.completedPlayerIds || !input.accepts(submission)) {
                return@updateGame game to RuleDecisionSubmissionUpdate(
                    Outcome.Error(GameError.InvalidRuleDecisionSubmission(gameId, playerId)),
                    null,
                )
            }
            val module = moduleRegistry.getModule(game.tableState.config)
            val resolver = resolverRegistry.find(module.id)
                ?: return@updateGame game to RuleDecisionSubmissionUpdate(
                    Outcome.Error(GameError.InvalidRuleDecisionSubmission(gameId, playerId)),
                    null,
                )
            val defaultRuleDecision = resolverRegistry.findDefaultConfig(module.id, game.tableState.config)
            if (defaultRuleDecision == null || game.flowConfig.ruleDecision == null) {
                throw RuleContractViolationException(
                    GameAbortReasonIds.RULE_EXECUTION_FAILED,
                    "Rule decision requires a time configuration for player input",
                )
            }
            if (!resolver.accepts(game.tableState, ruleDecision, playerId, submission, module)) {
                return@updateGame game to RuleDecisionSubmissionUpdate(
                    Outcome.Error(GameError.InvalidRuleDecisionSubmission(gameId, playerId)),
                    null,
                )
            }
            val submitted = ruleDecision.copy(
                submissionsByPlayerId = ruleDecision.submissionsByPlayerId + (playerId to submission),
            )
            var updated = game.copy(pendingRuleDecision = submitted)
            if (submitted.isComplete) {
                val resolution = resolver.resolve(game.tableState, submitted, module)
                updated = RuleDecisionWindows.advance(game, submitted, resolution, module, resolverRegistry)
            }
            updated to RuleDecisionSubmissionUpdate(
                Outcome.Success(Unit),
                HistoryEventDraft(
                    actorPlayerId = playerId,
                    fact = HistoryFact.RuleDecisionSubmitted(
                        timing = ruleDecision.timing,
                        stepId = ruleDecision.stepId,
                        stepIndex = ruleDecision.stepIndex,
                        submission = submission,
                        nextStepId = updated.pendingRuleDecision?.stepId,
                    ),
                ),
                presentationUpdate = if (submitted.isComplete) {
                    RuleDecisionPresentationUpdate(game.tableState, updated.tableState)
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
    private data class RuleDecisionSubmissionUpdate(
        val outcome: Outcome<Unit, GameError>,
        val historyDraft: HistoryEventDraft?,
        val presentationUpdate: RuleDecisionPresentationUpdate? = null,
    )
}
