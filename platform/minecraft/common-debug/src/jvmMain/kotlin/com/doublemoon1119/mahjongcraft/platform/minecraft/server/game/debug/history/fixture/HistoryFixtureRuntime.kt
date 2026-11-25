package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessFlowHistoryRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor

/**
 * 產生測試牌譜的無頭對局。
 *
 * - 對局本身與一般產生的牌譜相同。
 * - 另外提供檢查：腳本是否照順序執行完。
 *
 * @property runtime 實際的無頭對局。
 * @property script 這次產生的腳本進度。
 */
class HistoryFixtureRuntime private constructor(
    private val runtime: HeadlessHistoryMatchRuntime,
    private val script: HistoryFixtureScript,
) : HeadlessHistoryMatchRuntime by runtime {
    /**
     * 確認整場打完時腳本全部照順序執行完。
     *
     * @throws IllegalStateException 腳本沒有執行完或與實際對局不符時拋出。
     */
    fun verify() = script.verify()

    /** 建立測試牌譜對局的入口。 */
    companion object {
        /** 腳本 AI 只在隔離對局中登記的 key。 */
        private const val STRATEGY_KEY = "mahjongcraft:history_fixture"

        /**
         * 建立照腳本進行的測試牌譜對局：每個座位都使用照腳本做決定的 AI，種子固定。
         *
         * @param fixture 測試牌譜。
         * @param registries 與正式對局相同的規則登記；AI 策略換成腳本 AI。
         * @param extensionActions 把立直等規則擴充動作轉成命令。
         * @param failurePolicy 判斷 AI 策略丟出的失敗能否隔離。
         * @return 已開局、可逐步推進的對局。
         */
        suspend fun create(
            fixture: HistoryFixture,
            registries: HeadlessHistoryRegistries,
            extensionActions: ExtensionGameActionAiRegistry,
            failurePolicy: ExtensionFailurePolicy,
        ): HistoryFixtureRuntime {
            val script = HistoryFixtureScript(fixture)
            val strategies = MahjongAiStrategyRegistryImpl(defaultKey = STRATEGY_KEY).apply {
                register(MahjongAiStrategyDefinition(STRATEGY_KEY) { HistoryFixtureAiStrategy(script, extensionActions) })
                freeze()
            }
            val runtime = HeadlessFlowHistoryRuntime.create(
                scenario = fixture.scenario,
                registries = registries.copy(aiStrategyRegistry = strategies),
                decisionExecutor = AiDecisionExecutor.direct(failurePolicy = failurePolicy),
                matchSeed = fixture.matchSeed,
                roundRandomnessSource = script,
                aiStrategyKey = STRATEGY_KEY,
            )
            return HistoryFixtureRuntime(runtime, script)
        }
    }
}
