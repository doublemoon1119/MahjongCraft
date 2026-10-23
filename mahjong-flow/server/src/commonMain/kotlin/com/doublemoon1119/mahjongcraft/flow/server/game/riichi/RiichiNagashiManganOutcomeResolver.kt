package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SettlementExtrasResolver
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.SettlementExtrasOccasion
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 將日麻流局滿貫判定轉為 Flow 的 win-equivalent round outcome。
 *
 * @property ruleModuleId 套用的日麻系列規則模組（四人或三人）。
 */
class RiichiNagashiManganOutcomeResolver(
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI,
) : PostReactionRoundOutcomeResolver {
    override val id: String = RiichiRoundOutcomeIds.NAGASHI_MANGAN
    override val priority: Int = 100

    override fun resolve(tableState: TableState, ruleModule: MahjongRuleModule<*>): ResolvedRoundOutcome? {
        val riichiModule = ruleModule as? RiichiFamilyRuleModule<*> ?: return null
        val resolution = riichiModule.resolveNagashiMangan(tableState) ?: return null
        // 比照和牌分配供託與本場；多人成立時候選者從莊家起算排列。
        val extras = resolution.achieverPlayerIds.takeIf { it.isNotEmpty() }?.let { achieverIds ->
            SettlementExtrasResolver.resolve(
                state = tableState,
                module = riichiModule,
                occasion = SettlementExtrasOccasion.WIN,
                candidatePlayerIds = tableState.playersInTurnOrderFrom(tableState.dealerPlayerId, achieverIds),
            )
        }
        val finalDeltas = tableState.players.associate { player ->
            player.id to (resolution.scoreDeltas.getValue(player.id) + (extras?.scoreDeltas?.get(player.id) ?: 0))
        }
        val settledState = tableState.copy(
            players = tableState.players.map { player ->
                player.copy(score = player.score + finalDeltas.getValue(player.id))
            },
            dynamicRuleState = extras?.dynamicRuleState ?: tableState.dynamicRuleState,
        )
        val dealerId = tableState.dealerPlayerId
        return ResolvedRoundOutcome(
            id = id,
            settledTableState = settledState,
            beneficiaryPlayerIds = resolution.achieverPlayerIds,
            scoreDeltas = finalDeltas,
            stickPotCollectorPlayerIds = extras?.stickPotRecipientIds.orEmpty(),
            transitionDirective = if (dealerId in resolution.achieverPlayerIds) {
                RoundTransitionDirective.RepeatDealer
            } else {
                RoundTransitionDirective.PassToNextSeat
            },
            presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        )
    }
}
