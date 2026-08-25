package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/** 將日麻流局滿貫判定轉為 Flow 的 win-equivalent round outcome。 */
class RiichiNagashiManganOutcomeResolver : PostReactionRoundOutcomeResolver {
    override val id: String = BuiltInRoundOutcomeIds.NAGASHI_MANGAN
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI
    override val priority: Int = 100

    override fun resolve(tableState: TableState, ruleModule: MahjongRuleModule<*>): ResolvedRoundOutcome? {
        val riichiModule = ruleModule as? RiichiRuleModule ?: return null
        val resolution = riichiModule.resolveNagashiMangan(tableState) ?: return null
        val stickPot = riichiModule.collectStickPot(tableState)
        val collectorId = chooseStickPotCollector(tableState, resolution.achieverPlayerIds)
        val finalDeltas = tableState.players.associate { player ->
            val stickDelta = if (player.id == collectorId) stickPot?.second ?: 0 else 0
            player.id to (resolution.scoreDeltas.getValue(player.id) + stickDelta)
        }
        val settledState = tableState.copy(
            players = tableState.players.map { player ->
                player.copy(score = player.score + finalDeltas.getValue(player.id))
            },
            dynamicRuleState = stickPot?.first ?: tableState.dynamicRuleState,
        )
        val dealerId = tableState.players.first { it.currentWind == Wind.EAST }.id
        return ResolvedRoundOutcome(
            id = id,
            settledTableState = settledState,
            beneficiaryPlayerIds = resolution.achieverPlayerIds,
            scoreDeltas = finalDeltas,
            stickPotCollectorPlayerIds = setOfNotNull(collectorId).takeIf { (stickPot?.second ?: 0) > 0 }.orEmpty(),
            transitionDirective = if (dealerId in resolution.achieverPlayerIds) {
                RoundTransitionDirective.REPEAT_DEALER
            } else {
                RoundTransitionDirective.ADVANCE_DEALER
            },
            presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        )
    }

    /** 多人成立時依莊家起算的頭跳順位決定唯一供託收取者。 */
    private fun chooseStickPotCollector(tableState: TableState, achieverIds: Set<Uuid>): Uuid? {
        if (achieverIds.isEmpty()) return null
        if (achieverIds.size == 1) return achieverIds.first()
        val dealerId = tableState.players.first { it.currentWind == Wind.EAST }.id
        return tableState.nearestPlayerInTurnOrder(dealerId, achieverIds)
    }
}

/** 登記 bundled 日麻的流局滿貫 outcome resolver。 */
fun PostReactionRoundOutcomeResolverRegistry.registerRiichiNagashiManganOutcomeResolver() {
    register(RiichiNagashiManganOutcomeResolver())
}
