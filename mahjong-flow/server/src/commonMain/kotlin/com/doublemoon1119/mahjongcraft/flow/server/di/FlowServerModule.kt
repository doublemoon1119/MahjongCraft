package com.doublemoon1119.mahjongcraft.flow.server.di

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionReporter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceFailureReporter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceFollowUp
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * `:mahjong-flow-server` 的 Koin 模組。
 *
 * 絕大多數綁定靠 [ComponentScan] + 類別自身的 `@Factory` 或 `@Single` 標註自動完成；各 registry 的內容由
 * extension bootstrap 登記。`:mahjong-ai` 刻意不依賴 Koin（比照 `:mahjong-logic` 維持框架無關），
 * [MahjongAiStrategyRegistryImpl] 與 [OpponentModelRegistry] 因此不會被這裡的 [ComponentScan] 掃到，需要顯式提供。
 * 需要自訂建構參數或平台提供相依項的服務也保留在本模組明確組裝。
 */
@Module(includes = [FlowCommonModule::class])
@ComponentScan("com.doublemoon1119.mahjongcraft.flow.server")
class FlowServerModule {
    /**
     * 建立供擴充在啟動期登記動作評估器的 AI 註冊表。
     *
     * 實作位於框架無關的 `:mahjong-ai`，因此由流程組合模組明確建立。
     *
     * @param moduleRegistry 提供各規則模組及其合法動作判定。
     * @return 尚待擴充登記的動作評估器註冊表。
     */
    @Single
    fun extensionGameActionAiRegistry(moduleRegistry: MahjongModuleRegistry): ExtensionGameActionAiRegistry = ExtensionGameActionAiRegistry(moduleRegistry)

    /**
     * 建立 AI 策略 registry；未知或未指定的策略 key 退回初級。內建策略由 extension bootstrap 與其他內建項目一起登記，
     * 這裡不預先登記任何策略。實作位於框架無關的 `:mahjong-ai`，且需要指定預設策略 key，因此保留明確組裝。
     */
    @Single
    fun mahjongAiStrategyRegistry(): MahjongAiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = BuiltInAiStrategyKeys.BEGINNER)

    /**
     * 建立供規則 extension 登記對手模型的 registry；沒有登記的規則使用不具規則知識的對手模型。
     *
     * 實作位於框架無關的 `:mahjong-ai`，因此不由 Flow 的 component scan 掃描。
     */
    @Single
    fun opponentModelRegistry(): OpponentModelRegistry = OpponentModelRegistry()

    /**
     * 建立整個程式共用的 AI 決策執行器：在 [CoroutineDispatchers.aiDecision] 上呼叫策略，同時存在的策略工作上限為 AI 執行緒數的
     * [AiDecisionExecutor.CAPACITY_PER_THREAD] 倍。
     *
     * 容量由 runtime 平行度計算，不直接對應到可注入的參數；保留此工廠組裝調度器與平台回報器。
     *
     * @param dispatchers 提供 AI 決策調度器及平行度。
     * @param reporter 策略逾時與未結束工作等情況的回報器。
     * @param failurePolicy 平台提供的擴充失敗判斷，決定策略失敗能否改用固定結果。
     * @return 共用的 AI 決策執行器。
     */
    @Single
    fun aiDecisionExecutor(
        dispatchers: CoroutineDispatchers,
        @Provided reporter: AiDecisionReporter,
        @Provided failurePolicy: ExtensionFailurePolicy,
    ): AiDecisionExecutor = AiDecisionExecutor(
        dispatcher = dispatchers.aiDecision,
        capacity = dispatchers.aiDecisionParallelism * AiDecisionExecutor.CAPACITY_PER_THREAD,
        reporter = reporter,
        failurePolicy = failurePolicy,
    )

    /**
     * 建立共用的單局推進管理器，在目前執行階段的作用域與權威狀態調度器上推進遊戲。
     *
     * 使用 [AutomatedAdvanceManager.forCoordinator] 將協調器組成推進 callback，
     * 保留工廠方法的調度器、後續檢查與失敗回報組合，不改成一般建構子注入。
     *
     * @param appScope 管理目前執行階段協程生命週期的作用域。
     * @param dispatchers 提供權威狀態操作的調度器。
     * @param coordinator 負責遊戲流程的協調器。
     * @param followUp 推進後需執行的後續檢查。
     * @param failureReporter 推進失敗的回報器。
     * @return 共用的單局推進管理器。
     */
    @Single
    fun automatedAdvanceManager(
        appScope: AppCoroutineScope,
        dispatchers: CoroutineDispatchers,
        coordinator: GameFlowCoordinator,
        @Provided followUp: AutomatedAdvanceFollowUp,
        @Provided failureReporter: AutomatedAdvanceFailureReporter,
    ): AutomatedAdvanceManager = AutomatedAdvanceManager.forCoordinator(appScope, dispatchers.main, coordinator, followUp, failureReporter)
}
