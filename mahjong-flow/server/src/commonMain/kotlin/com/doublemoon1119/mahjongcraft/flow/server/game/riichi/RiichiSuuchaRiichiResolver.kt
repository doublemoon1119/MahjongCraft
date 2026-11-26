package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.CompletedGameActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolver
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule

/** 在立直捨牌完成後判定日麻四家立直。 */
class RiichiSuuchaRiichiResolver : PostActionExhaustiveDrawResolver {
    override val id: String = RiichiExhaustiveDrawReason.SuuchaRiichi.id
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI
    override val priority: Int = 100

    override fun resolve(context: CompletedGameActionContext, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason? {
        val riichiModule = ruleModule as? RiichiRuleModule ?: return null
        if (context.action !is GameAction.Discard) return null
        val actor = context.tableState.players.firstOrNull { it.id == context.actorPlayerId } ?: return null
        val actions = actor.actionHistory
        if (actions.getOrNull(actions.lastIndex - 1) != RIICHI_GAME_ACTION || actions.lastOrNull() != context.action) return null
        return riichiModule.resolveSuuchaRiichi(context.tableState)
    }
}
