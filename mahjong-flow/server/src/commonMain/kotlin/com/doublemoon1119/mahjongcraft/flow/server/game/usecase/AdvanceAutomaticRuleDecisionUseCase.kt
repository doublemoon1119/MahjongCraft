package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.RuleDecisionPresentationUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.uuid.Uuid

/**
 * 在呈現閒置後，收斂不需要玩家輸入的規則選擇步驟。
 *
 * @property gameRepository 讀取與更新權威遊戲。
 * @property moduleRegistry 依桌況設定取得規則模組。
 * @property resolverRegistry 取得規則提供的選擇流程解析器。
 * @property snapshotSynchronizer 更新選擇流程變更後的觀看者快照。
 * @property presentationPublisher 更新規則選擇結果改變的牌區與局況。
 */
class AdvanceAutomaticRuleDecisionUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RuleDecisionResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 收斂目前連續的自動步驟。
     *
     * @param gameId 對局 Uuid。
     * @return 權威 rule decision 或桌況是否實際改變。
     */
    suspend operator fun invoke(gameId: Uuid): Boolean {
        val update = gameRepository.updateGame(
            gameId,
            history = { _, _, result -> result.historyDrafts },
        ) { game ->
            val currentGame = game
                ?: return@updateGame null to AutomaticRuleDecisionUpdate(emptyList())
            val ruleDecision = currentGame.pendingRuleDecision
                ?: return@updateGame currentGame to AutomaticRuleDecisionUpdate(emptyList())
            if (ruleDecision.participantPlayerIds.isNotEmpty()) {
                return@updateGame currentGame to AutomaticRuleDecisionUpdate(emptyList())
            }
            val module = moduleRegistry.getModule(currentGame.tableState.config)
            val resolver = resolverRegistry.find(module.id)
                ?: return@updateGame currentGame to AutomaticRuleDecisionUpdate(emptyList())
            var updated = currentGame
            val drafts = buildList {
                repeat(MAX_AUTOMATIC_RULE_DECISION_STEPS) {
                    val currentRuleDecision = updated.pendingRuleDecision
                        ?: return@repeat
                    if (currentRuleDecision.participantPlayerIds.isNotEmpty()) return@repeat
                    val resolution = resolver.resolve(updated.tableState, currentRuleDecision, module)
                    val defaultRuleDecision = resolverRegistry.findDefaultConfig(module.id, updated.tableState.config)
                    if (resolution.nextStep?.participantPlayerIds?.isEmpty() == false &&
                        (defaultRuleDecision == null || updated.flowConfig.ruleDecision == null)
                    ) {
                        throw RuleContractViolationException(
                            GameAbortReasonIds.RULE_EXECUTION_FAILED,
                            "Rule decision requires a time configuration for player input",
                        )
                    }
                    updated = updated.copy(
                        tableState = resolution.tableState,
                        pendingRuleDecision = resolution.nextStep,
                    )
                    add(
                        HistoryEventDraft(
                            actorPlayerId = null,
                            fact = HistoryFact.RuleDecisionAutomaticallyResolved(
                                stepId = currentRuleDecision.stepId,
                                stepIndex = currentRuleDecision.stepIndex,
                                nextStepId = resolution.nextStep?.stepId,
                            ),
                        ),
                    )
                }
            }
            if (updated.pendingRuleDecision?.participantPlayerIds?.isEmpty() == true) {
                error("Rule decision did not converge after $MAX_AUTOMATIC_RULE_DECISION_STEPS automatic steps")
            }
            updated to AutomaticRuleDecisionUpdate(
                historyDrafts = drafts,
                presentationUpdate = RuleDecisionPresentationUpdate(currentGame.tableState, updated.tableState),
            )
        }
        if (update.changed) {
            update.presentationUpdate?.let { presentation ->
                presentation.publish(presentationPublisher, moduleRegistry.getModule(presentation.current.config))
            }
            snapshotSynchronizer.syncAll(gameId)
        }
        return update.changed
    }

    /**
     * 一次自動規則選擇收斂所產生的歷史事實。
     *
     * @property historyDrafts 此次收斂需要記錄的自動步驟事實。
     * @property presentationUpdate 此次收斂的桌況差異；沒有待處理步驟時為 null。
     */
    private data class AutomaticRuleDecisionUpdate(
        val historyDrafts: List<HistoryEventDraft>,
        val presentationUpdate: RuleDecisionPresentationUpdate? = null,
    ) {
        /** 是否至少完成一個選擇步驟。 */
        val changed: Boolean get() = historyDrafts.isNotEmpty()
    }

    private companion object {
        /** 單次呼叫最多自動收斂的步驟數，避免錯誤 resolver 無限產生空參與者步驟。 */
        const val MAX_AUTOMATIC_RULE_DECISION_STEPS: Int = 128
    }
}
