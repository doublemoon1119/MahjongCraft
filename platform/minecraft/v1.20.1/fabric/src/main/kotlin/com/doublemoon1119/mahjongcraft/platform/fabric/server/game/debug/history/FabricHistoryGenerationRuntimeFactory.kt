package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessFlowHistoryRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessStepTimer
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * 使用 extension 初始化完成後的 registry 建立隔離生成環境，讓生成的對局與正式對局使用同一套規則登記。
 *
 * @property moduleRegistry 規則模組。
 * @property aiStrategies AI 策略。
 * @property winCelebrationCueResolvers 胡牌演出提示。
 * @property postActionExhaustiveDrawResolvers 動作後的途中流局判定。
 * @property postReactionRoundOutcomeResolvers 回應結束後的本局結果判定。
 * @property winRoundContinuationResolvers 胡牌後本局是否繼續。
 * @property winSettlementDetailResolvers 胡牌詳情。
 * @property gameCommands 擴充命令 handler。
 */
@Single
class FabricHistoryGenerationRuntimeFactory(
    @Provided private val moduleRegistry: MahjongModuleRegistry,
    @Provided private val aiStrategies: MahjongAiStrategyRegistry,
    @Provided private val winCelebrationCueResolvers: WinCelebrationCueResolverRegistry,
    @Provided private val postActionExhaustiveDrawResolvers: PostActionExhaustiveDrawResolverRegistry,
    @Provided private val postReactionRoundOutcomeResolvers: PostReactionRoundOutcomeResolverRegistry,
    @Provided private val winRoundContinuationResolvers: WinRoundContinuationResolverRegistry,
    @Provided private val winSettlementDetailResolvers: WinSettlementDetailResolverRegistry,
    @Provided private val gameCommands: ExtensionGameCommandExecutorRegistry,
) : HistoryGenerationRuntimeFactory {
/**
     * 建立全部座位都是真 AI 的完整對局。
     *
     * @param scenario 規則與場長情境。
     * @return 不產生世界實體的隔離環境。
     */
    override suspend fun create(scenario: HeadlessHistoryScenario): HeadlessHistoryMatchRuntime = HeadlessFlowHistoryRuntime.create(
        scenario = scenario,
        registries = registries(),
    )

    /**
     * 在 [store] 中建立全部座位都是真 AI 的完整對局；多場共用同一個權威來源，如同正式伺服器上同時進行的多桌。
     *
     * @param scenario 規則與場長情境。
     * @param store 承載對局的共用權威來源。
     * @param stepTimer 這一桌的分項計時器。
     * @param decisionExecutor 呼叫 AI 策略的執行器。
     * @return 不產生世界實體的對局環境。
     */
    suspend fun createIn(
        scenario: HeadlessHistoryScenario,
        store: AuthoritativeStateStore,
        stepTimer: HeadlessStepTimer,
        decisionExecutor: AiDecisionExecutor,
    ): HeadlessHistoryMatchRuntime = HeadlessFlowHistoryRuntime.create(
        scenario = scenario,
        registries = registries(),
        store = store,
        stepTimer = stepTimer,
        decisionExecutor = decisionExecutor,
    )

    /** 正式對局使用的規則整合。 */
    private fun registries() = HeadlessHistoryRegistries(
        moduleRegistry = moduleRegistry,
        aiStrategyRegistry = aiStrategies,
        winCelebrationCueResolverRegistry = winCelebrationCueResolvers,
        postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolvers,
        postReactionRoundOutcomeResolverRegistry = postReactionRoundOutcomeResolvers,
        winRoundContinuationResolverRegistry = winRoundContinuationResolvers,
        winSettlementDetailResolverRegistry = winSettlementDetailResolvers,
        gameCommandRegistry = gameCommands,
    )
}
