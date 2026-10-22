package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.DrawFollowUpPresentationStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.history.actionScoredHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.recordAbortiveDraw
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpContext
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpDecision
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 在同一筆權威寫入內，依規則的摸牌後自動處理 policy 反覆移出並補牌。
 *
 * 每一步沿用玩家宣告移出手牌的同一套搶和判定、套用、收付與補牌驗證。每一步都必須讓牌山與保留區合計至少少一張，
 * 因此這串處理一定會結束；規則回傳無法完成的結果時丟出 [RuleContractViolationException]。
 */
internal object DrawFollowUpApplier {
    /**
     * 已完成移出與補牌的一步。
     *
     * @property action 自動執行的移出手牌動作。
     * @property setAsideTile 這一步移出的牌。
     * @property applied 套用、收付與補牌的結果。
     */
    data class CompletedStep(
        val action: GameAction.Extension,
        val setAsideTile: IdentifiedTile,
        val applied: SelfDeclarationApplier.Result.Applied,
    )

    /** 這串自動處理結束的方式。 */
    sealed interface Ending {
        /** 規則不再處理，玩家照常進入自己的回合。 */
        data object PlayerTurn : Ending

        /**
         * 其他玩家可以搶最後一步要移出的牌，已開啟搶和視窗並暫停。
         *
         * @property action 暫緩套用的移出手牌動作。
         * @property setAsideTile 暫緩移出的牌。
         * @property declaredState 牌已移出、尚未補牌的候選桌況，只供呈現使用。
         */
        data class RobbingWindowOpened(
            val action: GameAction.Extension,
            val setAsideTile: IdentifiedTile,
            val declaredState: TableState?,
        ) : Ending

        /**
         * 本局以流局結束：規則決定結束，或搶和時依一炮多響設定判定流局。
         *
         * @property reason 流局原因。
         * @property interruptedAction 因一炮多響判定流局而未成立的移出手牌動作；規則直接結束時為 null。
         * @property interruptedTile [interruptedAction] 要移出的牌；規則直接結束時為 null。
         */
        data class RoundEnded(
            val reason: ExhaustiveDrawReason,
            val interruptedAction: GameAction.Extension?,
            val interruptedTile: IdentifiedTile?,
        ) : Ending
    }

    /**
     * 整串自動處理的結果。
     *
     * @property tableState 這串處理完成後的權威桌況。
     * @property steps 依序完成的步驟。
     * @property ending 結束方式。
     */
    data class Result(
        val tableState: TableState,
        val steps: List<CompletedStep>,
        val ending: Ending,
    ) {
        /** 這串處理是否沒有做任何事。 */
        val isEmpty: Boolean
            get() = steps.isEmpty() && ending == Ending.PlayerTurn

        /** 依序完成的動作，供通知觀察者；包含暫停或未成立的移出與結束本局的流局。 */
        val publishedActions: List<GameAction>
            get() = steps.flatMap { step -> listOf(step.action, GameAction.Draw) } + when (val end = ending) {
                Ending.PlayerTurn -> emptyList()
                is Ending.RobbingWindowOpened -> listOf(end.action)
                is Ending.RoundEnded -> listOfNotNull(end.interruptedAction) + GameAction.ExhaustiveDraw(end.reason)
            }

        /**
         * 依實際順序產生這串處理的牌譜事件：每一步依序是移出、收付與補牌，最後是暫停的移出或結束本局的流局。
         *
         * @param playerId 摸牌的玩家。
         * @param before 交易前的完整桌況。
         * @param after 交易提交後的完整桌況。
         * @return 依序排列的事件草稿；沒有處理時為空清單。
         */
        fun historyDrafts(playerId: Uuid, before: TableState, after: TableState): List<HistoryEventDraft> = buildList {
            steps.forEach { step ->
                add(acceptedActionHistoryDraft(playerId, step.action, before, after, listOf(step.setAsideTile.id)))
                step.applied.scoring?.let { add(actionScoredHistoryDraft(it)) }
                add(acceptedActionHistoryDraft(playerId, GameAction.Draw, before, after, step.applied.drawnTiles.map { it.id }))
            }
            when (val end = ending) {
                Ending.PlayerTurn -> Unit
                is Ending.RobbingWindowOpened ->
                    add(acceptedActionHistoryDraft(playerId, end.action, before, after, listOf(end.setAsideTile.id)))
                is Ending.RoundEnded -> {
                    if (end.interruptedAction != null && end.interruptedTile != null) {
                        add(acceptedActionHistoryDraft(playerId, end.interruptedAction, before, after, listOf(end.interruptedTile.id)))
                    }
                    add(acceptedActionHistoryDraft(playerId, GameAction.ExhaustiveDraw(end.reason), before, after))
                }
            }
        }

        /** 依序交給平台呈現的步驟；暫停在搶和視窗時，最後一步只有移出、沒有補牌。 */
        val presentationSteps: List<DrawFollowUpPresentationStep>
            get() = steps.map { step ->
                DrawFollowUpPresentationStep(
                    action = step.action,
                    setAsideTileId = step.setAsideTile.id,
                    drawnTileId = step.applied.drawnTiles.last().id,
                    physicalWallTransitionPhases = step.applied.physicalWallTransitionPhases,
                    scoring = step.applied.scoring,
                )
            } + listOfNotNull(
                (ending as? Ending.RobbingWindowOpened)?.let { pending ->
                    DrawFollowUpPresentationStep(
                        action = pending.action,
                        setAsideTileId = pending.setAsideTile.id,
                        drawnTileId = null,
                        physicalWallTransitionPhases = emptyList(),
                        scoring = null,
                    )
                },
            )
    }

    /**
     * 從 [playerId] 剛摸到的牌開始執行自動處理。
     *
     * @param state 已套用這次摸牌的權威桌況；摸到的牌位於玩家的摸牌位。
     * @param playerId 摸牌的玩家。
     * @param source 這次摸牌的來源。
     * @param module 對局採用的規則模組。
     * @return 整串自動處理的結果；規則不處理時沒有任何步驟。
     * @throws RuleContractViolationException 規則要求的處理無法完成時。
     */
    fun run(
        state: TableState,
        playerId: Uuid,
        source: DrawSource,
        module: MahjongRuleModule<*>,
    ): Result {
        val policy = module.createDrawFollowUpPolicy()
        val steps = mutableListOf<CompletedStep>()
        var current = state
        var currentSource = source
        while (true) {
            val player = current.players.first { it.id == playerId }
            val drawnTile = player.hand.lastDrawn ?: return Result(current, steps, Ending.PlayerTurn)
            val decision = policy.resolve(
                DrawFollowUpContext(
                    tableState = current,
                    playerId = playerId,
                    drawnTile = drawnTile,
                    source = currentSource,
                    completedStepCount = steps.size,
                ),
            )
            when (decision) {
                DrawFollowUpDecision.None -> return Result(current, steps, Ending.PlayerTurn)
                is DrawFollowUpDecision.EndRound -> return Result(
                    tableState = current.recordAbortiveDraw(decision.reason, declarerId = playerId, module = module),
                    steps = steps,
                    ending = Ending.RoundEnded(decision.reason, interruptedAction = null, interruptedTile = null),
                )

                is DrawFollowUpDecision.SetAside -> {
                    val action = decision.action
                    val setAsideTile = module.tileSetAsideBy(player, action)
                        ?: violation("set-aside action ${action.value.id} has no tile to remove")
                    when (val resolution = TileSetAsideResolver.resolve(current, playerId, action, setAsideTile, module)) {
                        is TileSetAsideResolver.Resolution.RobbingWindowOpened -> return Result(
                            tableState = resolution.tableState,
                            steps = steps,
                            ending = Ending.RobbingWindowOpened(action, setAsideTile, resolution.declaredState),
                        )

                        is TileSetAsideResolver.Resolution.AbortiveDraw -> return Result(
                            tableState = resolution.tableState,
                            steps = steps,
                            ending = Ending.RoundEnded(resolution.reason, action, setAsideTile),
                        )

                        is TileSetAsideResolver.Resolution.Rejected ->
                            violation("set-aside action ${action.value.id} could not be completed: ${resolution.reasonId}")

                        is TileSetAsideResolver.Resolution.Applied -> {
                            val applied = resolution.applied
                            if (applied.tableState.remainingSourceTileCount() >= current.remainingSourceTileCount() ||
                                applied.drawnTiles.isEmpty()
                            ) {
                                violation("set-aside action ${action.value.id} did not draw a replacement tile")
                            }
                            steps += CompletedStep(action, setAsideTile, applied)
                            current = applied.tableState
                            currentSource = DrawSource.Supplemental(action)
                        }
                    }
                }
            }
        }
    }

    /** 活牌與保留區合計剩下的牌數。 */
    private fun TableState.remainingSourceTileCount(): Int = tileWall.remainingCount + reservedWallTiles.size

    /** 以規則錯誤中止這串處理。 */
    private fun violation(detail: String): Nothing = throw RuleContractViolationException(
        GameAbortReasonIds.RULE_EXECUTION_FAILED,
        "Draw follow-up failed: $detail",
    )
}
