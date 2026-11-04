package com.doublemoon1119.mahjongcraft.flow.server.game.taiwan

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.CompletedGameActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolver
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TAIWAN_REVEAL_FLOWER_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule

/** 在第四槓的補牌與和牌機會結束後，於第一張捨牌完成時判定四槓流局。 */
class TaiwanFourKansResolver : PostActionExhaustiveDrawResolver {
    override val id: String = TaiwanExhaustiveDrawReason.FourKans.id
    override val ruleModuleId: String = BuiltInRuleModuleIds.TAIWAN
    override val priority: Int = 100

    override fun resolve(
        context: CompletedGameActionContext,
        ruleModule: MahjongRuleModule<*>,
    ): ExhaustiveDrawReason? {
        val taiwanModule = ruleModule as? TaiwanRuleModule ?: return null
        if (context.action !is GameAction.Discard) return null
        val actor = context.tableState.players.firstOrNull { it.id == context.actorPlayerId } ?: return null
        var actionsBeforeDraw = actor.actionHistory
            .takeIf { actions -> actions.lastOrNull() == context.action }
            ?.dropLast(1)
            ?: return null
        while (
            actionsBeforeDraw.lastOrNull() == GameAction.Draw &&
            actionsBeforeDraw.getOrNull(actionsBeforeDraw.lastIndex - 1) == TAIWAN_REVEAL_FLOWER_ACTION
        ) {
            actionsBeforeDraw = actionsBeforeDraw.dropLast(2)
        }
        if (
            actionsBeforeDraw.lastOrNull() != GameAction.Draw ||
            actionsBeforeDraw.getOrNull(actionsBeforeDraw.lastIndex - 1) !is GameAction.Kan
        ) {
            return null
        }
        return taiwanModule.resolveFourKans(context.tableState)
    }
}
