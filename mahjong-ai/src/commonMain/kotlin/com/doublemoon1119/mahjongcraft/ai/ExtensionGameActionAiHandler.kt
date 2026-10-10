package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/** 將一種擴充動作轉換成 AI 可執行命令的策略。 */
fun interface ExtensionGameActionAiHandler<A : ExtensionGameAction> {
    /** 依目前情境與這一局的 [module] 建立所有可安全執行的命令候選。 */
    fun createCandidates(
        action: A,
        context: AiDecisionContext,
        module: MahjongRuleModule<*>,
    ): List<ExtensionCommandCandidate>
}
