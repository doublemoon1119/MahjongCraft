package com.doublemoon1119.mahjongcraft.testing.flow.server

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionAiDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.RuleDecisionWinPresentationStager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceAutomaticRuleDecisionUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.BeginRuleDecisionUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SubmitRuleDecisionUseCase
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * 測試用的規則選擇流程元件：以同一組依賴建立開窗、提交、自動推進與 AI 提交，供組裝對局流程的測試直接傳入。
 *
 * @param gameRepository 對局存取。
 * @param moduleRegistry 規則模組。
 * @param snapshotSynchronizer 快照同步。
 * @param presentationPublisher 呈現發布。
 * @param handSortPreferenceStore 手牌排序偏好。
 * @param winPresentationHandoff 胡牌呈現交接。
 * @param winSettlementDetailResolverRegistry 胡牌詳情。
 * @param aiStrategyRegistry AI 策略。
 * @param visibilityPolicy AI 視角的快照政策。
 * @param resolverRegistry 規則選擇流程；預設為沒有任何規則選擇的已凍結 registry。
 * @param abortGameHandler 規則選擇違反契約時的中止處理；預設不中止。
 * @param decisionExecutor 呼叫 AI 策略的執行器。
 * @property begin 開啟規則選擇視窗。
 * @property submit 提交規則選擇。
 * @property advanceAutomatic 收斂不需玩家輸入的步驟。
 * @property aiDriver 解析 AI 的規則選擇提交。
 */
class RuleDecisionTestUseCases(
    gameRepository: GameRepository,
    moduleRegistry: MahjongModuleRegistry,
    snapshotSynchronizer: GameSnapshotSynchronizer,
    presentationPublisher: GamePresentationPublisher,
    handSortPreferenceStore: HandSortPreferenceStore,
    winPresentationHandoff: WinPresentationHandoff,
    winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry,
    aiStrategyRegistry: MahjongAiStrategyRegistry,
    visibilityPolicy: GameVisibilityPolicy,
    resolverRegistry: RuleDecisionResolverRegistry = emptyRuleDecisionResolverRegistry(),
    abortGameHandler: GameAbortHandler = GameAbortHandler { _, _, _ -> false },
    decisionExecutor: AiDecisionExecutor = AiDecisionExecutor.direct(failurePolicy = ExtensionFailurePolicy.Default),
) {
    private val winPresentationStager = RuleDecisionWinPresentationStager(winPresentationHandoff, winSettlementDetailResolverRegistry)

    val begin: BeginRuleDecisionUseCase = BeginRuleDecisionUseCase(
        gameRepository = gameRepository,
        moduleRegistry = moduleRegistry,
        resolverRegistry = resolverRegistry,
        snapshotSynchronizer = snapshotSynchronizer,
        abortGameHandler = abortGameHandler,
    )

    val submit: SubmitRuleDecisionUseCase = SubmitRuleDecisionUseCase(
        gameRepository = gameRepository,
        moduleRegistry = moduleRegistry,
        resolverRegistry = resolverRegistry,
        snapshotSynchronizer = snapshotSynchronizer,
        presentationPublisher = presentationPublisher,
        winPresentationStager = winPresentationStager,
        handSortPreferenceStore = handSortPreferenceStore,
    )

    val advanceAutomatic: AdvanceAutomaticRuleDecisionUseCase = AdvanceAutomaticRuleDecisionUseCase(
        gameRepository = gameRepository,
        moduleRegistry = moduleRegistry,
        resolverRegistry = resolverRegistry,
        snapshotSynchronizer = snapshotSynchronizer,
        presentationPublisher = presentationPublisher,
        winPresentationStager = winPresentationStager,
        handSortPreferenceStore = handSortPreferenceStore,
    )

    val aiDriver: RuleDecisionAiDriver = RuleDecisionAiDriver(
        gameRepository = gameRepository,
        moduleRegistry = moduleRegistry,
        resolverRegistry = resolverRegistry,
        aiStrategyRegistry = aiStrategyRegistry,
        visibilityPolicy = visibilityPolicy,
        decisionExecutor = decisionExecutor,
    )
}

/**
 * 沒有任何規則選擇的已凍結 registry。
 *
 * @return 空的規則選擇 registry。
 */
fun emptyRuleDecisionResolverRegistry(): RuleDecisionResolverRegistry = RuleDecisionResolverRegistry().apply { freeze() }

/**
 * 沒有任何規則選擇時的開窗用例：不會開窗，只照常寫入排定的流程。
 *
 * @param gameRepository 對局存取。
 * @param moduleRegistry 規則模組。
 * @param snapshotSynchronizer 快照同步。
 * @return 開窗用例。
 */
fun noRuleDecisionBeginUseCase(
    gameRepository: GameRepository,
    moduleRegistry: MahjongModuleRegistry,
    snapshotSynchronizer: GameSnapshotSynchronizer,
): BeginRuleDecisionUseCase = BeginRuleDecisionUseCase(
    gameRepository = gameRepository,
    moduleRegistry = moduleRegistry,
    resolverRegistry = emptyRuleDecisionResolverRegistry(),
    snapshotSynchronizer = snapshotSynchronizer,
    abortGameHandler = GameAbortHandler { _, _, _ -> false },
)
