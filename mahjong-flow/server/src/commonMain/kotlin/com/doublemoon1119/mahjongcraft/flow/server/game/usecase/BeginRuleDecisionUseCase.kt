package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.uuid.Uuid

/**
 * 發牌後依規則建立第一個規則選擇步驟。
 *
 * @property gameRepository 讀取與更新權威遊戲。
 * @property moduleRegistry 依桌況設定取得規則模組。
 * @property resolverRegistry 取得規則提供的選擇流程解析器。
 * @property snapshotSynchronizer 更新選擇流程變更後的觀看者快照。
 * @property abortGameHandler 規則契約無法安全繼續時，以失敗交易讀到的對局為基準中止它。
 */
class BeginRuleDecisionUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RuleDecisionResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val abortGameHandler: GameAbortHandler,
) {
    /**
     * 建立指定對局的第一個選擇步驟；規則沒有步驟時維持 null。
     *
     * @param gameId 欲開始選擇流程的對局。
     * @return 建立結果；對局不存在時回傳錯誤。
     * @throws RuleContractViolationException 規則需要玩家準備但沒有時間設定時；對局會先嘗試中止。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<Unit, GameError> {
        var contractBasis: Game? = null
        val result = try {
            gameRepository.updateGame(
                gameId,
                history = { _, _, update -> update.historyDraft?.let(::listOf).orEmpty() },
            ) { game ->
                if (game == null) {
                    return@updateGame null to RuleDecisionBeginUpdate(
                        Outcome.Error(GameError.GameNotFound(gameId)),
                        null,
                    )
                }
                if (game.pendingRuleDecision != null) {
                    return@updateGame game to RuleDecisionBeginUpdate(
                        Outcome.Success(Unit),
                        null,
                    )
                }
                val module = moduleRegistry.getModule(game.tableState.config)
                val resolver = resolverRegistry.find(module.id)
                    ?: return@updateGame game to RuleDecisionBeginUpdate(Outcome.Success(Unit), null)
                val firstStep = resolver.begin(game.tableState, module)
                val defaultRuleDecision = resolverRegistry.findDefaultConfig(module.id, game.tableState.config)
                if (firstStep?.participantPlayerIds?.isNotEmpty() == true &&
                    (defaultRuleDecision == null || game.flowConfig.ruleDecision == null)
                ) {
                    contractBasis = game
                    throw RuleContractViolationException(
                        GameAbortReasonIds.RULE_EXECUTION_FAILED,
                        "Rule decision requires a time configuration for player input",
                    )
                }
                game.copy(pendingRuleDecision = firstStep) to RuleDecisionBeginUpdate(
                    Outcome.Success(Unit),
                    firstStep?.let {
                        HistoryEventDraft(
                            actorPlayerId = null,
                            fact = HistoryFact.RuleDecisionStarted(it.stepId, it.stepIndex),
                        )
                    },
                )
            }
        } catch (failure: RuleContractViolationException) {
            contractBasis?.let { basis ->
                abortGameHandler.abort(basis, failure.reasonId, failure.message ?: "Rule decision contract violation")
            }
            throw failure
        }
        if (result.outcome is Outcome.Success) snapshotSynchronizer.syncAll(gameId)
        return result.outcome
    }

    /**
     * 原子更新後的選擇流程結果與可選歷史事實。
     *
     * @property outcome use case 對呼叫端回傳的結果。
     * @property historyDraft 開始新選擇步驟時要記錄的事實；沒有新步驟時為 null。
     */
    private data class RuleDecisionBeginUpdate(
        val outcome: Outcome<Unit, GameError>,
        val historyDraft: HistoryEventDraft?,
    )
}
