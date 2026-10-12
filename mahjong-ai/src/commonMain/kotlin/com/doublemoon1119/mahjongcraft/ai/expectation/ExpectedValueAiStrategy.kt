package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * 以期望值選擇行動的 AI 策略。
 *
 * 規則事實來自規則模組的向聽計算與規則查詢，對各玩家的估計來自依規則登記的對手模型，策略本身不含任何特定規則的判斷；
 * [level] 決定可使用的資訊，[parameters] 決定估計時使用的數值。輸入相同且注入的對手模型與擴充動作 handler
 * 都是確定性的時，同一局面會得到相同決策。
 *
 * 每次決策都建立新的 [ExpectedValueEvaluator]，估計用的快取只存在於該次決策，不會在不同呼叫間共用。
 * 若要並行呼叫同一策略，注入的規則模組登記表、對手模型登記表與模型、擴充動作登記表與 handler 都必須支援並行使用。
 *
 * @property level 可使用的資訊範圍。
 * @property moduleRegistry 依對局設定取得規則模組。
 * @property extensionActionRegistry 將規則擴充動作轉成命令候選。
 * @property opponentModels 依規則取得對手模型。
 * @property parameters 估計參數。
 */
class ExpectedValueAiStrategy(
    val level: InformationLevel,
    private val moduleRegistry: MahjongModuleRegistry,
    private val extensionActionRegistry: ExtensionGameActionAiRegistry,
    private val opponentModels: OpponentModelRegistry,
    val parameters: ExpectationParameters = ExpectationParameters.DEFAULT,
) : MahjongAiStrategy {
    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand {
        val module = moduleRegistry.getModule(context.snapshot.config)
        return ExpectedValueEvaluator(
            level = level,
            parameters = parameters,
            module = module,
            opponentModel = opponentModels.create(module, level.readingDepth),
            context = context,
        ).decide(extensionActionRegistry)
    }
}
