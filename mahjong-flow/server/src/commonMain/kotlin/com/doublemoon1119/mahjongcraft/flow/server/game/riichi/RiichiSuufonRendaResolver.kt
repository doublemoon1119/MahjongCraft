package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.CompletedGameActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolver
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule

/** 在捨牌完成後判定日麻四風連打。 */
class RiichiSuufonRendaResolver : PostActionExhaustiveDrawResolver {
    override val id: String = RiichiExhaustiveDrawReason.SuufonRenda.id
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI
    override val priority: Int = 100

    override fun resolve(context: CompletedGameActionContext, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason? {
        val riichiModule = ruleModule as? RiichiRuleModule ?: return null
        if (context.action !is GameAction.Discard) return null
        return riichiModule.resolveSuufonRenda(context.tableState)
    }
}
