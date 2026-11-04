package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanFlowerFlow
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPendingFlowerFollowUp
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanSpecialFlowerWin
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** 台灣麻將局前補花與特殊花胡的可恢復規則選擇流程。 */
object TaiwanFlowerRuleDecisionResolver : RuleDecisionResolver {
    override val ruleModuleId: String = BuiltInRuleModuleIds.TAIWAN

    /** 特殊花胡需要玩家接受或放棄，因此使用一般規則選擇時間。 */
    override fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig) = RuleDecisionConfig(30.seconds).takeIf { ruleConfig is TaiwanRuleConfig }

    /** 發牌後若有人持有花牌，先建立不需玩家輸入的補花步驟。 */
    override fun begin(
        timing: RuleDecisionTiming,
        tableState: TableState,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionStep? {
        if (ruleModule.config !is TaiwanRuleConfig) return null
        return when (timing) {
            RuleDecisionTiming.ROUND_START -> processStep(0).takeIf {
                tableState.players.any { player -> player.hand.standingTiles.any { tile -> TaiwanTileTypes.isFlower(tile.tile) } }
            }

            RuleDecisionTiming.DRAW_FOLLOW_UP -> processDrawFollowUpStep(0).takeIf {
                tableState.components[TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP] != null
            }

            else -> null
        }
    }

    /** 只接受花胡資格玩家提交接受或放棄。 */
    override fun accepts(
        tableState: TableState,
        decision: PendingRuleDecision,
        playerId: Uuid,
        submission: RuleDecisionSubmission,
        ruleModule: MahjongRuleModule<*>,
    ): Boolean = submission is RuleDecisionSubmission.Choice &&
        submission.optionId in setOf(ACCEPT_OPTION, PASS_OPTION) &&
        playerId in decision.participantPlayerIds

    /** 逾時一律放棄花胡並繼續補花。 */
    override fun fallbackSubmission(
        tableState: TableState,
        decision: PendingRuleDecision,
        playerId: Uuid,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionSubmission = RuleDecisionSubmission.Choice(PASS_OPTION)

    /** 套用自動補花或玩家的花胡選擇。 */
    override fun resolve(
        tableState: TableState,
        decision: PendingRuleDecision,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionResolution {
        require(ruleModule.config is TaiwanRuleConfig) { "Taiwan flower flow requires Taiwan config" }
        val result = when (decision.stepId) {
            PROCESS_STEP -> TaiwanFlowerFlow.process(tableState, openingOrder(tableState))
            PROCESS_DRAW_FOLLOW_UP_STEP -> {
                val pending = requireNotNull(tableState.components[TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP]) {
                    "Taiwan draw follow-up is missing its pending flower"
                }
                val flower = tableState.players
                    .first { it.id == pending.playerId }
                    .tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]
                    ?.tiles
                    ?.singleOrNull { it.id == pending.tileId }
                    ?: error("Taiwan draw follow-up flower is missing")
                TaiwanFlowerFlow.processRevealed(tableState, pending.playerId, flower)
            }
            FLOWER_WIN_STEP -> {
                val winnerId = decision.participantPlayerIds.single()
                val submission = decision.submissionsByPlayerId.getValue(winnerId) as RuleDecisionSubmission.Choice
                TaiwanFlowerFlow.resolve(reconstructPending(tableState, winnerId, decision.timing), submission.optionId == ACCEPT_OPTION)
            }

            else -> error("Unknown Taiwan flower step: ${decision.stepId}")
        }
        return resolution(result, decision.stepIndex)
    }

    /** 將純規則結果轉成規則選擇流程結果。 */
    private fun resolution(result: TaiwanFlowerFlow.Result, stepIndex: Int): RuleDecisionResolution = when (result) {
        is TaiwanFlowerFlow.Result.Completed -> RuleDecisionResolution(result.tableState.withoutPendingFlower(), null)
        is TaiwanFlowerFlow.Result.Exhausted -> RuleDecisionResolution(
            result.tableState.withoutPendingFlower().copy(
                players = result.tableState.players.map { player ->
                    player.recordAction(GameAction.ExhaustiveDraw(TaiwanExhaustiveDrawReason.NoSupplementalTile))
                },
            ),
            null,
        )

        is TaiwanFlowerFlow.Result.AwaitingDecision -> RuleDecisionResolution(
            result.pending.tableState.withPendingFlower(
                result.pending.revealingPlayerId,
                result.pending.revealedFlower.id,
            ),
            flowerWinStep(result.pending.opportunity.winnerId, stepIndex + 1),
        )

        is TaiwanFlowerFlow.Result.Won -> {
            val winnerId = result.opportunity.winnerId
            val scored = result.tableState.copy(
                players = result.tableState.players.map { player ->
                    val delta = if (player.id == winnerId) {
                        result.settlement.totalGained
                    } else {
                        -(result.settlement.paymentsByPlayerId[player.id] ?: 0)
                    }
                    val updated = if (delta == 0) player else player.copy(score = player.score + delta)
                    if (player.id == winnerId) updated.recordAction(GameAction.Tsumo) else updated
                },
            )
            RuleDecisionResolution(scored.withoutPendingFlower(), null)
        }
    }

    /** 從已公開花區重建當前唯一的特殊花胡資格。 */
    private fun reconstructPending(
        tableState: TableState,
        winnerId: Uuid,
        timing: RuleDecisionTiming,
    ): TaiwanFlowerFlow.Pending {
        if (timing == RuleDecisionTiming.DRAW_FOLLOW_UP) {
            val pending = requireNotNull(tableState.components[TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP])
            val flower = tableState.players.first { it.id == pending.playerId }
                .tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]
                ?.tiles
                ?.singleOrNull { it.id == pending.tileId }
                ?: error("Taiwan draw follow-up flower is missing")
            val opportunity = requireNotNull(TaiwanSpecialFlowerWin.findOpportunity(tableState, pending.playerId, flower))
            return TaiwanFlowerFlow.Pending(
                tableState = tableState,
                opportunity = opportunity,
                playerOrderIds = listOf(pending.playerId),
                playerOrderIndex = 0,
                revealingPlayerId = pending.playerId,
                revealedFlower = flower,
                replacementCompleted = opportunity.type == TaiwanTaiType.EIGHT_FLOWERS,
            )
        }
        val candidates = tableState.players.mapNotNull { revealer ->
            val revealed = revealer.tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles?.lastOrNull() ?: return@mapNotNull null
            TaiwanSpecialFlowerWin.findOpportunity(tableState, revealer.id, revealed)?.let { opportunity ->
                Triple(revealer.id, revealed, opportunity)
            }
        }.filter { (_, _, opportunity) -> opportunity.winnerId == winnerId }
        val (revealerId, revealed, opportunity) = candidates.single()
        val order = openingOrder(tableState)
        return TaiwanFlowerFlow.Pending(
            tableState = tableState,
            opportunity = opportunity,
            playerOrderIds = order,
            playerOrderIndex = order.indexOf(revealerId),
            revealingPlayerId = revealerId,
            revealedFlower = revealed,
            replacementCompleted = opportunity.type == TaiwanTaiType.EIGHT_FLOWERS,
        )
    }

    /** 莊家起依固定行牌順序排列玩家。 */
    private fun openingOrder(tableState: TableState): List<Uuid> = tableState.players.indices.map { offset ->
        tableState.players[(tableState.dealerIndex + offset) % tableState.players.size].id
    }

    /** 建立自動補花步驟。 */
    private fun processStep(index: Int) = RuleDecisionStep(PROCESS_STEP, index, emptyMap())

    /** 建立摸牌後自動續跑的步驟。 */
    private fun processDrawFollowUpStep(index: Int) = RuleDecisionStep(PROCESS_DRAW_FOLLOW_UP_STEP, index, emptyMap())

    /** 建立花胡接受／放棄步驟。 */
    private fun flowerWinStep(winnerId: Uuid, index: Int) = RuleDecisionStep(
        FLOWER_WIN_STEP,
        index,
        mapOf(winnerId to RuleDecisionInputSpec.SingleChoice(listOf(ACCEPT_OPTION, PASS_OPTION))),
    )

    private const val PROCESS_STEP = "mahjongcraft:taiwan/process_flowers"
    private const val PROCESS_DRAW_FOLLOW_UP_STEP = "mahjongcraft:taiwan/process_draw_follow_up"
    private const val FLOWER_WIN_STEP = "mahjongcraft:taiwan/special_flower_win"
    private const val ACCEPT_OPTION = "mahjongcraft:taiwan/accept_flower_win"
    private const val PASS_OPTION = "mahjongcraft:taiwan/pass_flower_win"
}

/** 清除已完成的摸牌後花牌處理標記。 */
private fun TableState.withoutPendingFlower(): TableState = copy(
    components = components.without(TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP),
)

/** 更新尚待玩家選擇的花牌身分。 */
private fun TableState.withPendingFlower(playerId: Uuid, tileId: Uuid): TableState = copy(
    components = components.with(
        TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP,
        TaiwanPendingFlowerFollowUp(playerId, tileId),
    ),
)
