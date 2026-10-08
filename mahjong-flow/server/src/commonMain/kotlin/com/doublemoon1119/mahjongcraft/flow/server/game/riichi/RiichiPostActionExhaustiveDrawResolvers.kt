package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.CompletedGameActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolver
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleModule
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

/**
 * 在槓後補摸與嶺上自摸機會結束後的第一張捨牌完成時判定日麻四槓散了。
 *
 * @property ruleModuleId 套用的日麻系列規則模組（四人或三人）。
 */
class RiichiSuukanNagareResolver(
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI,
) : PostActionExhaustiveDrawResolver {
    override val id: String = RiichiExhaustiveDrawReason.SuukanNagare.id
    override val priority: Int = 100

    override fun resolve(context: CompletedGameActionContext, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason? {
        val riichiModule = ruleModule as? RiichiFamilyRuleModule<*> ?: return null
        if (context.action !is GameAction.Discard) return null
        val actor = context.tableState.players.firstOrNull { it.id == context.actorPlayerId } ?: return null
        val actionsBeforeDiscard = actor.actionHistory
            .takeIf { it.lastOrNull() == context.action }
            ?.dropLast(1)
            ?: return null
        var actionsBeforeDraw = if (actionsBeforeDiscard.lastOrNull() == RIICHI_GAME_ACTION) {
            actionsBeforeDiscard.dropLast(1)
        } else {
            actionsBeforeDiscard
        }
        // 槓之後接著拔北再補牌，仍算是槓後的第一張捨牌。
        while (actionsBeforeDraw.lastOrNull() == GameAction.Draw && actionsBeforeDraw.getOrNull(actionsBeforeDraw.lastIndex - 1) == PULL_NORTH_GAME_ACTION) {
            actionsBeforeDraw = actionsBeforeDraw.dropLast(2)
        }
        if (actionsBeforeDraw.lastOrNull() != GameAction.Draw || actionsBeforeDraw.getOrNull(actionsBeforeDraw.lastIndex - 1) !is GameAction.Kan) {
            return null
        }
        return riichiModule.resolveSuukanNagare(context.tableState)
    }
}
