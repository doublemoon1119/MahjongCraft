package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule

/** 將日麻四風連打判定接上 [PostActionTrigger.DiscardCompleted] 時機。 */
class RiichiSuufonRendaResolver : PostActionExhaustiveDrawResolver {
    override val id: String = RiichiExhaustiveDrawReason.SuufonRenda.id
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI
    override val priority: Int = 100

    override fun resolve(trigger: PostActionTrigger, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason? {
        val riichiModule = ruleModule as? RiichiRuleModule ?: return null
        val discardCompleted = trigger as? PostActionTrigger.DiscardCompleted ?: return null
        return riichiModule.resolveSuufonRenda(discardCompleted.tableState)
    }
}

/** 將日麻四家立直判定接上 [PostActionTrigger.RiichiDeclared] 時機。 */
class RiichiSuuchaRiichiResolver : PostActionExhaustiveDrawResolver {
    override val id: String = RiichiExhaustiveDrawReason.SuuchaRiichi.id
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI
    override val priority: Int = 100

    override fun resolve(trigger: PostActionTrigger, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason? {
        val riichiModule = ruleModule as? RiichiRuleModule ?: return null
        val riichiDeclared = trigger as? PostActionTrigger.RiichiDeclared ?: return null
        return riichiModule.resolveSuuchaRiichi(riichiDeclared.tableState)
    }
}

/** 將日麻四槓散了判定接上 [PostActionTrigger.KanDeclared] 時機。 */
class RiichiSuukanNagareResolver : PostActionExhaustiveDrawResolver {
    override val id: String = RiichiExhaustiveDrawReason.SuukanNagare.id
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI
    override val priority: Int = 100

    override fun resolve(trigger: PostActionTrigger, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason? {
        val riichiModule = ruleModule as? RiichiRuleModule ?: return null
        val kanDeclared = trigger as? PostActionTrigger.KanDeclared ?: return null
        return riichiModule.resolveSuukanNagare(kanDeclared.tableState)
    }
}

/** 登記 bundled 日麻的主動觸發途中流局 resolver（四風連打／四家立直／四槓散了）。 */
fun PostActionExhaustiveDrawResolverRegistry.registerRiichiPostActionExhaustiveDrawResolvers() {
    register(RiichiSuufonRendaResolver())
    register(RiichiSuuchaRiichiResolver())
    register(RiichiSuukanNagareResolver())
}
