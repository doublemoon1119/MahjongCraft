package com.doublemoon1119.mahjongcraft.flow.server.game.taiwan

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.CompletedGameActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolver
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule

/** 在捨牌反應結束後判定已啟用的台灣麻將四風連打。 */
class TaiwanFourWindsResolver : PostActionExhaustiveDrawResolver {
    override val id: String = TaiwanExhaustiveDrawReason.FourWinds.id
    override val ruleModuleId: String = BuiltInRuleModuleIds.TAIWAN
    override val priority: Int = 100

    override fun resolve(
        context: CompletedGameActionContext,
        ruleModule: MahjongRuleModule<*>,
    ): ExhaustiveDrawReason? {
        val taiwanModule = ruleModule as? TaiwanRuleModule ?: return null
        if (context.action !is GameAction.Discard) return null
        return taiwanModule.resolveFourWinds(context.tableState)
    }
}
