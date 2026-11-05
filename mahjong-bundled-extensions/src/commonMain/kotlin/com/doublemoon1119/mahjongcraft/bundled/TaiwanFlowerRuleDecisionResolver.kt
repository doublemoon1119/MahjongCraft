package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.flow.common.game.model.DrawFollowUpPresentationStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionWinOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanSpecialFlowerWinDetail
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionPlayerAreaPresentationStep
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionWin
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TAIWAN_REVEAL_FLOWER_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanFlowerFlow
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanFlowerRuleDecisionIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPendingFlowerFollowUp
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanSpecialFlowerWin
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileReplacementRounds
import com.doublemoon1119.mahjongcraft.logic.table.drawDealerOpeningTile
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
        submission.optionId in setOf(
            TaiwanFlowerRuleDecisionIds.ACCEPT_FLOWER_WIN,
            TaiwanFlowerRuleDecisionIds.PASS_FLOWER_WIN,
        ) &&
        playerId in decision.participantPlayerIds

    /** 逾時一律放棄花胡並繼續補花。 */
    override fun fallbackSubmission(
        tableState: TableState,
        decision: PendingRuleDecision,
        playerId: Uuid,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionSubmission = RuleDecisionSubmission.Choice(TaiwanFlowerRuleDecisionIds.PASS_FLOWER_WIN)

    /** 套用自動補花或玩家的花胡選擇。 */
    override fun resolve(
        tableState: TableState,
        decision: PendingRuleDecision,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionResolution {
        require(ruleModule.config is TaiwanRuleConfig) { "Taiwan flower flow requires Taiwan config" }
        val openingDraw = if (decision.stepId == TaiwanFlowerRuleDecisionIds.PROCESS_FLOWERS) tableState.drawDealerOpeningTile(ruleModule) else null
        val result = when (decision.stepId) {
            TaiwanFlowerRuleDecisionIds.PROCESS_FLOWERS -> TaiwanFlowerFlow.process(openingDraw?.tableState ?: tableState, openingOrder(tableState))
            TaiwanFlowerRuleDecisionIds.PROCESS_DRAW_FOLLOW_UP -> {
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
            TaiwanFlowerRuleDecisionIds.SPECIAL_FLOWER_WIN -> {
                val winnerId = decision.participantPlayerIds.single()
                val submission = decision.submissionsByPlayerId.getValue(winnerId) as RuleDecisionSubmission.Choice
                TaiwanFlowerFlow.resolve(
                    reconstructPending(tableState, winnerId, decision.timing),
                    submission.optionId == TaiwanFlowerRuleDecisionIds.ACCEPT_FLOWER_WIN,
                )
            }

            else -> error("Unknown Taiwan flower step: ${decision.stepId}")
        }
        val isOpening = decision.timing == RuleDecisionTiming.ROUND_START
        val settled = if (isOpening) result.withOpeningReplacementsStanding() else result
        val openingDrawStep = openingDraw?.let { listOf(RuleDecisionPlayerAreaPresentationStep.Draw(tableState.dealerPlayerId, it.tile.id)) }.orEmpty()
        return resolution(settled, decision.stepIndex) { steps -> openingDrawStep + steps.toPresentationSteps(isOpening) }
    }

    /**
     * 將純規則結果轉成規則選擇流程結果。
     *
     * @param result 補花流程的結果。
     * @param stepIndex 目前的選擇步驟序號。
     * @param presentationSteps 把補花的牌區變化轉成呈現步驟。
     * @return 規則選擇流程結果。
     */
    private fun resolution(
        result: TaiwanFlowerFlow.Result,
        stepIndex: Int,
        presentationSteps: (List<TileReplacementRounds.Step>) -> List<RuleDecisionPlayerAreaPresentationStep>,
    ): RuleDecisionResolution = when (result) {
        is TaiwanFlowerFlow.Result.Completed -> RuleDecisionResolution(
            tableState = result.tableState.withoutPendingFlower(),
            nextStep = null,
            playerAreaPresentationSteps = presentationSteps(result.steps),
        )
        is TaiwanFlowerFlow.Result.Exhausted -> RuleDecisionResolution(
            result.tableState.withoutPendingFlower().copy(
                players = result.tableState.players.map { player ->
                    player.recordAction(GameAction.ExhaustiveDraw(TaiwanExhaustiveDrawReason.NoSupplementalTile))
                },
            ),
            nextStep = null,
            playerAreaPresentationSteps = presentationSteps(result.steps),
        )

        is TaiwanFlowerFlow.Result.AwaitingDecision -> RuleDecisionResolution(
            result.pending.tableState.withPendingFlower(
                result.pending.revealingPlayerId,
                result.pending.revealedFlower.id,
                result.pending.position.replacementsDue,
            ),
            nextStep = flowerWinStep(result.pending.opportunity.winnerId, stepIndex + 1),
            playerAreaPresentationSteps = presentationSteps(result.steps),
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
            RuleDecisionResolution(
                tableState = scored.withoutPendingFlower(),
                nextStep = null,
                win = RuleDecisionWin(
                    outcome = RuleDecisionWinOutcome(
                        outcomeId = when (result.opportunity.type) {
                            TaiwanTaiType.EIGHT_FLOWERS -> TaiwanRoundOutcomeIds.EIGHT_FLOWERS
                            TaiwanTaiType.SEVEN_FLOWERS -> TaiwanRoundOutcomeIds.SEVEN_FLOWERS
                            else -> error("Unsupported Taiwan special flower win: ${result.opportunity.type}")
                        },
                        winnerPlayerIds = setOf(winnerId),
                        responsiblePlayerIds = setOfNotNull(result.opportunity.payerId),
                        winningTileId = result.winningFlower.id,
                    ),
                    scoreDeltas = scored.players.associate { player ->
                        val previousScore = result.tableState.players.first { it.id == player.id }.score
                        player.id to (player.score - previousScore)
                    },
                    extensionDetail = TaiwanSpecialFlowerWinDetail(
                        taiTypeId = result.opportunity.type.id,
                        tai = result.opportunity.type.tai,
                    ),
                ),
            )
        }
    }

    /** 以等待選擇時記下的亮花者與花牌，重建這次特殊花胡資格；局首補花依莊家起的行牌順序續跑，途中補花只處理亮花者。 */
    private fun reconstructPending(
        tableState: TableState,
        winnerId: Uuid,
        timing: RuleDecisionTiming,
    ): TaiwanFlowerFlow.Pending {
        val pending = requireNotNull(tableState.components[TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP]) {
            "Taiwan special flower win is missing its pending flower"
        }
        val flower = tableState.players.first { it.id == pending.playerId }
            .tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]
            ?.tiles
            ?.singleOrNull { it.id == pending.tileId }
            ?: error("Taiwan pending flower is missing")
        val opportunity = requireNotNull(TaiwanSpecialFlowerWin.findOpportunity(tableState, pending.playerId, flower)) {
            "Taiwan pending flower no longer forms a special flower win"
        }
        require(opportunity.winnerId == winnerId) { "Taiwan special flower win belongs to another player" }
        val order = if (timing == RuleDecisionTiming.ROUND_START) openingOrder(tableState) else listOf(pending.playerId)
        val revealerIndex = order.indexOf(pending.playerId)
        val position = if (pending.replacementsDue > 0) {
            TileReplacementRounds.Position(revealerIndex, pending.replacementsDue)
        } else {
            TileReplacementRounds.Position(playerOrderIndex = (revealerIndex + 1) % order.size, replacementsDue = 0)
        }
        return TaiwanFlowerFlow.Pending(
            tableState = tableState,
            opportunity = opportunity,
            playerOrderIds = order,
            position = position,
            revealingPlayerId = pending.playerId,
            revealedFlower = flower,
        )
    }

    /** 莊家起依固定行牌順序排列玩家。 */
    private fun openingOrder(tableState: TableState): List<Uuid> = tableState.players.indices.map { offset ->
        tableState.players[(tableState.dealerIndex + offset) % tableState.players.size].id
    }

    /** 建立自動補花步驟。 */
    private fun processStep(index: Int) = RuleDecisionStep(TaiwanFlowerRuleDecisionIds.PROCESS_FLOWERS, index, emptyMap())

    /** 建立摸牌後自動續跑的步驟。 */
    private fun processDrawFollowUpStep(index: Int) = RuleDecisionStep(TaiwanFlowerRuleDecisionIds.PROCESS_DRAW_FOLLOW_UP, index, emptyMap())

    /** 建立花胡接受／放棄步驟。 */
    private fun flowerWinStep(winnerId: Uuid, index: Int) = RuleDecisionStep(
        TaiwanFlowerRuleDecisionIds.SPECIAL_FLOWER_WIN,
        index,
        mapOf(
            winnerId to RuleDecisionInputSpec.SingleChoice(
                listOf(TaiwanFlowerRuleDecisionIds.ACCEPT_FLOWER_WIN, TaiwanFlowerRuleDecisionIds.PASS_FLOWER_WIN),
            ),
        ),
    )
}

/**
 * 將補花的牌區變化轉成共用的呈現步驟；同一位玩家緊接著的公開與補牌合成一步。
 *
 * 局首補花的花可能在立牌中，以「從手牌移出並補牌」依序呈現。牌局中摸到的花在摸牌時已經呈現，這裡從它的補牌開始：
 * 補到的牌又是花時，以「摸到後從摸牌位移出並補牌」呈現，不另外呈現摸到它；最後留下的補牌以一般摸牌呈現。
 *
 * @param isOpening 是否為局首補花。
 * @return 依原順序排列的呈現步驟。
 */
private fun List<TileReplacementRounds.Step>.toPresentationSteps(isOpening: Boolean): List<RuleDecisionPlayerAreaPresentationStep> {
    val steps = mutableListOf<RuleDecisionPlayerAreaPresentationStep>()
    var index = 0
    while (index < size) {
        val step = this[index]
        val setAsideNext = (getOrNull(index + 1) as? TileReplacementRounds.Step.SetAside)?.tiles.orEmpty()
        if (!isOpening && step is TileReplacementRounds.Step.Replaced && step.tiles.any { it in setAsideNext }) {
            index++
            continue
        }
        val replaced = (getOrNull(index + 1) as? TileReplacementRounds.Step.Replaced)
            ?.takeIf { step is TileReplacementRounds.Step.SetAside && it.playerId == step.playerId }
        val setAside = (step as? TileReplacementRounds.Step.SetAside)?.tiles.orEmpty()
        val drawn = replaced?.tiles ?: (step as? TileReplacementRounds.Step.Replaced)?.tiles.orEmpty()
        steps += when {
            isOpening -> RuleDecisionPlayerAreaPresentationStep.Replacement(step.playerId, setAside.map { it.id }, drawn.map { it.id })
            setAside.isEmpty() -> RuleDecisionPlayerAreaPresentationStep.Draw(step.playerId, drawn.single().id)
            else -> RuleDecisionPlayerAreaPresentationStep.FollowUp(
                playerId = step.playerId,
                followUpStep = DrawFollowUpPresentationStep(
                    action = TAIWAN_REVEAL_FLOWER_ACTION,
                    setAsideTileId = setAside.single().id,
                    drawnTileIds = drawn.map { it.id },
                    physicalWallTransitionPhases = emptyList(),
                    scoring = null,
                ),
            )
        }
        index += if (replaced != null) 2 else 1
    }
    return steps
}

/** 局首補花時，閒家補到的牌併入立牌；莊家保留首次行動所需的摸牌位。 */
private fun TaiwanFlowerFlow.Result.withOpeningReplacementsStanding(): TaiwanFlowerFlow.Result = when (this) {
    is TaiwanFlowerFlow.Result.Completed -> copy(tableState = tableState.withOpeningReplacementsStanding())
    is TaiwanFlowerFlow.Result.AwaitingDecision -> copy(pending = pending.copy(tableState = pending.tableState.withOpeningReplacementsStanding()))
    is TaiwanFlowerFlow.Result.Exhausted, is TaiwanFlowerFlow.Result.Won -> this
}

/** 將閒家摸牌位上的補牌併入立牌。 */
private fun TableState.withOpeningReplacementsStanding(): TableState = copy(
    players = players.map { player ->
        val drawn = player.hand.lastDrawn
        if (player.id == dealerPlayerId || drawn == null) player else player.copy(hand = player.hand.copy(tiles = player.hand.tiles + drawn, lastDrawn = null))
    },
)

/** 清除已完成的摸牌後花牌處理標記。 */
private fun TableState.withoutPendingFlower(): TableState = copy(
    components = components.without(TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP),
)

/** 更新尚待玩家選擇的花牌身分與亮花者尚未補的張數。 */
private fun TableState.withPendingFlower(playerId: Uuid, tileId: Uuid, replacementsDue: Int): TableState = copy(
    components = components.with(
        TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP,
        TaiwanPendingFlowerFollowUp(playerId, tileId, replacementsDue),
    ),
)
