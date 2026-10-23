package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 開發環境指令建立受控 rule decision state 時使用的日麻測試 resolver。
 *
 * @property ruleModuleId 此 resolver 支援的規則模組 ID。
 */
class DebugRuleDecisionResolver(override val ruleModuleId: String) : RuleDecisionResolver {

    /** 正常發牌不自動建立測試步驟，確保開發環境的正常對局不受影響。 */
    override fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>): PendingRuleDecision? = null

    /** 測試步驟收齊提交後直接完成，不修改規則桌況。 */
    override fun resolve(
        tableState: TableState,
        ruleDecision: PendingRuleDecision,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionResolution = RuleDecisionResolution(tableState, null)
}
