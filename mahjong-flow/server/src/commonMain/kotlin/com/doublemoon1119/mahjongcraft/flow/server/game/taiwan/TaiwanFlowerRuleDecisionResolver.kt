package com.doublemoon1119.mahjongcraft.flow.server.game.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.event.PublicGameActionIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTimeSource
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionWinOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.TileReplacementRemoval
import com.doublemoon1119.mahjongcraft.flow.common.game.model.TileReplacementRound
import com.doublemoon1119.mahjongcraft.flow.common.game.model.accepts
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanSpecialFlowerWinDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionWin
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
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
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.RoundConclusion
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileReplacementRounds
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.drawDealerOpeningTile
import kotlin.uuid.Uuid

/** 台灣麻將局前補花與特殊花胡的可恢復規則選擇流程。 */
object TaiwanFlowerRuleDecisionResolver : RuleDecisionResolver {
    override val ruleModuleId: String = BuiltInRuleModuleIds.TAIWAN

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
    ): Boolean = playerId in decision.participantPlayerIds && decision.inputSpecsByPlayerId[playerId]?.accepts(submission) == true

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
                    submission.optionId != TaiwanFlowerRuleDecisionIds.PASS_FLOWER_WIN,
                )
            }

            else -> error("Unknown Taiwan flower step: ${decision.stepId}")
        }
        val isOpening = decision.timing == RuleDecisionTiming.ROUND_START
        val settled = if (isOpening) result.withOpeningReplacementsStanding() else result
        val openingDrawStep = openingDraw?.let { listOf(TableStep(it.tableState, emptyList())) }.orEmpty()
        // 局首補完牌之後各家一起理牌，補進手牌還沒理過的牌才收進手牌。
        val sortStep = (settled as? TaiwanFlowerFlow.Result.Completed)?.takeIf { isOpening }?.let { completed ->
            listOf(TableStep(completed.tableState, listOf(TableStepEvent.HandsSorted(completed.tableState.players.mapTo(mutableSetOf()) { it.id }))))
        }.orEmpty()
        return resolution(settled, decision.stepIndex) { steps -> openingDrawStep + steps.toTableSteps(isOpening) + sortStep }
    }

    /**
     * 將純規則結果轉成規則選擇流程結果。
     *
     * @param result 補花流程的結果。
     * @param stepIndex 目前的選擇步驟序號。
     * @param presentationSteps 把補花的牌區變化轉成依序的中間步驟。
     * @return 規則選擇流程結果。
     */
    private fun resolution(
        result: TaiwanFlowerFlow.Result,
        stepIndex: Int,
        presentationSteps: (List<TileReplacementRounds.Step>) -> List<TableStep>,
    ): RuleDecisionResolution = when (result) {
        is TaiwanFlowerFlow.Result.Completed -> RuleDecisionResolution(
            tableState = result.tableState.withoutPendingFlower(),
            nextStep = null,
            steps = presentationSteps(result.steps),
            replacementRounds = result.steps.toReplacementRounds(),
        )
        is TaiwanFlowerFlow.Result.Exhausted -> RuleDecisionResolution(
            result.tableState.withoutPendingFlower().copy(
                players = result.tableState.players.map { player ->
                    player.recordAction(GameAction.ExhaustiveDraw(TaiwanExhaustiveDrawReason.NoSupplementalTile))
                },
                turnPhase = TurnPhase.RoundOver(RoundConclusion.AbortiveDraw(TaiwanExhaustiveDrawReason.NoSupplementalTile)),
            ),
            nextStep = null,
            steps = presentationSteps(result.steps),
            replacementRounds = result.steps.toReplacementRounds(),
        )

        is TaiwanFlowerFlow.Result.AwaitingDecision -> RuleDecisionResolution(
            result.pending.tableState.withPendingFlower(
                result.pending.revealingPlayerId,
                result.pending.revealedFlower.id,
                result.pending.position.replacementsDue,
                result.pending.position.round,
            ),
            nextStep = flowerWinStep(result.pending, stepIndex + 1),
            steps = presentationSteps(result.steps),
            replacementRounds = result.steps.toReplacementRounds(),
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
                turnPhase = TurnPhase.RoundOver(RoundConclusion.Win(setOf(winnerId))),
            )
            RuleDecisionResolution(
                tableState = scored.withoutPendingFlower(),
                nextStep = null,
                win = RuleDecisionWin(
                    outcome = RuleDecisionWinOutcome(
                        outcomeId = when (result.opportunity.taiResult.type) {
                            TaiwanTaiType.EightFlowers -> TaiwanRoundOutcomeIds.EIGHT_FLOWERS
                            TaiwanTaiType.SevenFlowers -> TaiwanRoundOutcomeIds.SEVEN_FLOWERS
                            else -> error("Unsupported Taiwan special flower win: ${result.opportunity.taiResult.type}")
                        },
                        winnerPlayerIds = setOf(winnerId),
                        responsiblePlayerIds = setOfNotNull(result.opportunity.payerId),
                        winningTileId = result.winningFlower.id,
                        // 花胡與手牌組成無關，不攤牌。
                        revealsWinnerHands = false,
                    ),
                    scoreDeltas = scored.players.associate { player ->
                        val previousScore = result.tableState.players.first { it.id == player.id }.score
                        player.id to (player.score - previousScore)
                    },
                    extensionDetail = TaiwanSpecialFlowerWinDetail(
                        taiTypeId = TaiwanWinSettlementIds.tai(result.opportunity.taiResult.type),
                        tai = result.opportunity.taiResult.tai,
                        winningFlowerId = result.winningFlower.id,
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
            TileReplacementRounds.Position(revealerIndex, pending.replacementsDue, pending.resumeRound)
        } else {
            TileReplacementRounds.Position(playerOrderIndex = (revealerIndex + 1) % order.size, replacementsDue = 0, round = pending.resumeRound)
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

    /**
     * 建立花胡接受／放棄步驟。胡牌的選項沿用一般胡牌動作：八仙過海是自摸，七搶一搶別人的花是胡別人的牌；選項展示這次
     * 和牌用到的花牌，搶來的花排在最後。放棄以一般的跳過呈現。
     *
     * @param pending 等待選擇的花胡資格。
     * @param index 選擇步驟序號。
     * @return 花胡選擇步驟。
     */
    private fun flowerWinStep(pending: TaiwanFlowerFlow.Pending, index: Int): RuleDecisionStep {
        val winnerId = pending.opportunity.winnerId
        val winnerFlowers = pending.tableState.players.first { it.id == winnerId }.tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles.orEmpty()
        val flowers = winnerFlowers.filterNot { it.id == pending.revealedFlower.id } + pending.revealedFlower
        val winOption = if (pending.opportunity.payerId == null) PublicGameActionIds.TSUMO else PublicGameActionIds.RON
        return RuleDecisionStep(
            TaiwanFlowerRuleDecisionIds.SPECIAL_FLOWER_WIN,
            index,
            mapOf(
                winnerId to RuleDecisionInputSpec.SingleChoice(
                    optionIds = listOf(winOption, TaiwanFlowerRuleDecisionIds.PASS_FLOWER_WIN),
                    previewTileIdsByOptionId = mapOf(winOption to flowers.map { it.id }),
                    passOptionId = TaiwanFlowerRuleDecisionIds.PASS_FLOWER_WIN,
                ),
            ),
            timeSource = RuleDecisionTimeSource.ACTION_TIME,
        )
    }
}

/**
 * 依輪次整理每一輪各玩家移出的花牌；補到的牌不公開，不列入。
 *
 * @return 依輪次排列的換牌結果。
 */
private fun List<TileReplacementRounds.Step>.toReplacementRounds(): List<TileReplacementRound> = filterIsInstance<TileReplacementRounds.Step.SetAside>()
    .groupBy { it.round }
    .map { (round, setAsides) ->
        TileReplacementRound(
            round = round,
            removals = setAsides.groupBy { it.playerId }.map { (playerId, steps) -> TileReplacementRemoval(playerId, steps.flatMap { step -> step.tiles.map { it.id } }) },
        )
    }

/**
 * 將補花的牌區變化依序轉成中間步驟，每一次移出或補牌是一步。
 *
 * 補到的牌照一般摸牌先放在摸牌位，再補下一張時，摸牌位上的牌接到立牌最右邊；局首補完後各家一起理牌時才收進立牌。
 * 牌局中亮出的花以移出手牌的動作呈現。
 *
 * @param isOpening 是否為局首補花。
 * @return 依原順序排列的中間步驟。
 */
private fun List<TileReplacementRounds.Step>.toTableSteps(isOpening: Boolean): List<TableStep> = map { step ->
    val state = step.tableState
    val events = if (!isOpening && step is TileReplacementRounds.Step.SetAside) {
        listOf(TableStepEvent.ActionDeclared(step.playerId, TAIWAN_REVEAL_FLOWER_ACTION))
    } else {
        emptyList()
    }
    TableStep(state, events)
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

/** 更新尚待玩家選擇的花牌身分、亮花者尚未補的張數與繼續補花時的輪次。 */
private fun TableState.withPendingFlower(
    playerId: Uuid,
    tileId: Uuid,
    replacementsDue: Int,
    resumeRound: Int,
): TableState = copy(
    components = components.with(
        TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP,
        TaiwanPendingFlowerFollowUp(playerId, tileId, replacementsDue, resumeRound),
    ),
)
