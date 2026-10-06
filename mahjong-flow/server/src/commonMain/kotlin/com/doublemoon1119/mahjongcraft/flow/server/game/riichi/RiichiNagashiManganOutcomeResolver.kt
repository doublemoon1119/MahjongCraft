package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolver
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 將日麻流局滿貫判定轉為 Flow 的 win-equivalent round outcome。 */
class RiichiNagashiManganOutcomeResolver : PostReactionRoundOutcomeResolver {
    override val id: String = RiichiRoundOutcomeIds.NAGASHI_MANGAN
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI
    override val priority: Int = 100

    override fun resolve(tableState: TableState, ruleModule: MahjongRuleModule<*>): ResolvedRoundOutcome? {
        val riichiModule = ruleModule as? RiichiRuleModule ?: return null
        val resolution = riichiModule.resolveNagashiMangan(tableState) ?: return null
        val stickPot = riichiModule.collectStickPot(tableState)
        val collectorId = chooseStickPotCollector(tableState, resolution.achieverPlayerIds)
        // 本場比照自摸，由收下供託的同一位成立者收取
        val comboBonusPayments = collectorId
            ?.let {
                riichiModule.resolveComboBonusPayments(
                    tableState = tableState,
                    winnerId = it,
                    discarderId = null,
                    resolution = null,
                )
            }
            .orEmpty()
        val finalDeltas = tableState.players.associate { player ->
            val collectorDelta = if (player.id == collectorId) (stickPot?.second ?: 0) + comboBonusPayments.values.sum() else 0
            val comboBonusPayment = comboBonusPayments[player.id] ?: 0
            player.id to (resolution.scoreDeltas.getValue(player.id) + collectorDelta - comboBonusPayment)
        }
        val settledState = tableState.copy(
            players = tableState.players.map { player ->
                player.copy(score = player.score + finalDeltas.getValue(player.id))
            },
            dynamicRuleState = stickPot?.first ?: tableState.dynamicRuleState,
        )
        val dealerId = tableState.dealerPlayerId
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

    /** 多人成立時依莊家起算的頭跳順位決定唯一的供託與本場收取者。 */
    private fun chooseStickPotCollector(tableState: TableState, achieverIds: Set<Uuid>): Uuid? {
        if (achieverIds.isEmpty()) return null
        if (achieverIds.size == 1) return achieverIds.first()
        val dealerId = tableState.dealerPlayerId
        return tableState.nearestPlayerInTurnOrder(dealerId, achieverIds)
    }
}
