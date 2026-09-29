package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * 以期望值選擇行動的 AI 策略。
 *
 * 規則知識全部來自規則模組的向聽計算與局面評估，策略本身不含任何特定規則的判斷；[level] 決定可使用的資訊，
 * [parameters] 決定估計時使用的數值。同一個局面一定得到同一個決策。
 *
 * @property level 可使用的資訊範圍。
 * @property moduleRegistry 依對局設定取得規則模組。
 * @property extensionActionRegistry 將規則擴充動作轉成命令候選。
 * @property parameters 估計參數。
 */
class ExpectedValueAiStrategy(
    val level: InformationLevel,
    private val moduleRegistry: MahjongModuleRegistry,
    private val extensionActionRegistry: ExtensionGameActionAiRegistry,
    val parameters: ExpectationParameters = ExpectationParameters.DEFAULT,
) : MahjongAiStrategy {
    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = ExpectedValueEvaluator(
        level = level,
        parameters = parameters,
        module = moduleRegistry.getModule(context.snapshot.config),
        context = context,
    ).decide(extensionActionRegistry)
}
