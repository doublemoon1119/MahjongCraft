package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 將一種擴充動作轉換成 AI 可執行命令的策略。
 *
 * @param A 此策略能處理的擴充動作型別。
 */
fun interface ExtensionGameActionAiHandler<A : ExtensionGameAction> {
    /**
     * 依目前情境與這一局的規則模組建立命令候選，不直接執行命令。
     *
     * @param action 要轉換的擴充動作。
     * @param context 本次決策可見的桌況與合法動作。
     * @param module 本局使用的規則模組。
     * @return 可供策略評估的命令候選；沒有候選時為空清單。
     */
    fun createCandidates(
        action: A,
        context: AiDecisionContext,
        module: MahjongRuleModule<*>,
    ): List<ExtensionCommandCandidate>
}
