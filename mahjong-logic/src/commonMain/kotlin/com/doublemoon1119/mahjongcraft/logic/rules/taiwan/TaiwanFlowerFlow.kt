package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 依指定座位順序處理台灣麻將補花，並在特殊花胡成立時暫停等待選擇。
 *
 * 本物件不保存可變狀態；[Pending] 帶齊恢復流程所需的身分，重新載入後可以用同一份資料繼續。
 */
object TaiwanFlowerFlow {
    /** 一次已成立、需要依序呈現的牌區變化。 */
    sealed interface Step {
        /** 接收牌區變化的玩家。 */
        val playerId: Uuid

        /**
         * 從手牌公開一張花牌，並在可以補牌時接著補牌。
         *
         * @property playerId 公開花牌並接收補牌的玩家。
         * @property flower 公開到花牌區的牌。
         * @property replacementTile 從保留區補入手牌的牌；尚待花胡選擇或牌源耗盡時為 null。
         */
        data class FlowerRevealed(
            override val playerId: Uuid,
            val flower: IdentifiedTile,
            val replacementTile: IdentifiedTile?,
        ) : Step

        /**
         * 補入先前公開花牌時暫停的補牌，例如花胡放過或接受之後。
         *
         * @property playerId 接收補牌的玩家。
         * @property replacementTile 從保留區補入摸牌位的牌。
         */
        data class ReplacementDrawn(
            override val playerId: Uuid,
            val replacementTile: IdentifiedTile,
        ) : Step
    }

    /** 一次補花流程的結果。 */
    sealed interface Result {
        /** 所有指定玩家都已沒有待補花牌。 */
        data class Completed(
            val tableState: TableState,
            val steps: List<Step>,
        ) : Result

        /** 已公開最後一張花，但牌牆已無合法補牌，本局應流局。 */
        data class Exhausted(
            val tableState: TableState,
            val revealingPlayerId: Uuid,
            val revealedFlower: IdentifiedTile,
            val steps: List<Step>,
        ) : Result

        /** 特殊花胡成立，必須等待資格玩家接受或放棄。 */
        data class AwaitingDecision(
            val pending: Pending,
            val steps: List<Step>,
        ) : Result

        /** 玩家接受特殊花胡，呼叫端應套用付款並結束本局。 */
        data class Won(
            val tableState: TableState,
            val opportunity: TaiwanSpecialFlowerWin.Opportunity,
            val settlement: WinSettlementResult,
            val winningFlower: IdentifiedTile,
        ) : Result
    }

    /**
     * 一次可恢復的特殊花胡選擇。
     *
     * @property tableState 最後一張花已公開的桌況；八仙過海也已完成本次補牌。
     * @property opportunity 本次唯一的花胡資格。
     * @property playerOrderIds 本輪局前補花的固定玩家順序；途中補花只包含當事人。
     * @property playerOrderIndex 目前處理到的玩家索引。
     * @property revealingPlayerId 本次公開最後一張花的玩家。
     * @property revealedFlower 本次剛公開的花牌。
     * @property replacementCompleted 是否已在開啟選擇前完成補牌。
     */
    data class Pending(
        val tableState: TableState,
        val opportunity: TaiwanSpecialFlowerWin.Opportunity,
        val playerOrderIds: List<Uuid>,
        val playerOrderIndex: Int,
        val revealingPlayerId: Uuid,
        val revealedFlower: IdentifiedTile,
        val replacementCompleted: Boolean,
    )

    /**
     * 從指定玩家開始補花；每位玩家補完所有連續花後才輪到下一位。
     *
     * @param tableState 補花前的桌況。
     * @param playerOrderIds 玩家處理順序；局前由莊家開始，途中只放入摸牌者。
     * @param playerOrderIndex 要開始或恢復的玩家索引。
     * @return 完成、耗盡或等待花胡選擇的結果。
     */
    fun process(
        tableState: TableState,
        playerOrderIds: List<Uuid>,
        playerOrderIndex: Int = 0,
    ): Result {
        require(playerOrderIds.distinct().size == playerOrderIds.size) { "Flower replacement player order must be unique" }
        require(playerOrderIds.all { id -> tableState.players.any { it.id == id } }) {
            "Flower replacement players must belong to the table"
        }
        var state = tableState
        var index = playerOrderIndex
        val steps = mutableListOf<Step>()
        while (index < playerOrderIds.size) {
            val playerId = playerOrderIds[index]
            val flower = state.players.first { it.id == playerId }.hand.standingTiles
                .firstOrNull { TaiwanTileTypes.isFlower(it.tile) }
            if (flower == null) {
                index++
                continue
            }
            val revealed = TaiwanFlowerReplacement.reveal(state, playerId, flower)
                ?: error("Selected flower could not be revealed")
            if (state.tileWall.remainingCount == 0) {
                steps += Step.FlowerRevealed(playerId, flower, replacementTile = null)
                return Result.Exhausted(revealed, playerId, flower, steps)
            }
            val beforeReplacement = TaiwanSpecialFlowerWin.findOpportunity(revealed, playerId, flower)
            if (beforeReplacement?.type == TaiwanTaiType.SEVEN_FLOWERS) {
                return Result.AwaitingDecision(
                    Pending(revealed, beforeReplacement, playerOrderIds, index, playerId, flower, replacementCompleted = false),
                    steps + Step.FlowerRevealed(playerId, flower, replacementTile = null),
                )
            }
            val supplemented = TaiwanFlowerReplacement.supplement(revealed, playerId)
                ?: return Result.Exhausted(
                    revealed,
                    playerId,
                    flower,
                    steps + Step.FlowerRevealed(playerId, flower, replacementTile = null),
                )
            steps += Step.FlowerRevealed(playerId, flower, supplemented.replacementTile)
            state = supplemented.tableState
            val afterReplacement = TaiwanSpecialFlowerWin.findOpportunity(state, playerId, flower)
            if (afterReplacement?.type == TaiwanTaiType.EIGHT_FLOWERS) {
                return Result.AwaitingDecision(
                    Pending(state, afterReplacement, playerOrderIds, index, playerId, flower, replacementCompleted = true),
                    steps,
                )
            }
        }
        return Result.Completed(state, steps)
    }

    /**
     * 接續一張已移到公開花區、尚未補牌的花牌。
     *
     * @param tableState 花牌已公開的桌況。
     * @param playerId 公開花牌的玩家。
     * @param flower 已公開的花牌。
     * @return 花胡選擇、補牌耗盡或完成後續連續補花的結果。
     */
    fun processRevealed(
        tableState: TableState,
        playerId: Uuid,
        flower: IdentifiedTile,
    ): Result {
        val opportunity = TaiwanSpecialFlowerWin.findOpportunity(tableState, playerId, flower)
        if (opportunity?.type == TaiwanTaiType.SEVEN_FLOWERS) {
            return Result.AwaitingDecision(
                Pending(tableState, opportunity, listOf(playerId), 0, playerId, flower, replacementCompleted = false),
                listOf(Step.FlowerRevealed(playerId, flower, replacementTile = null)),
            )
        }
        val supplemented = TaiwanFlowerReplacement.supplement(tableState, playerId)
            ?: return Result.Exhausted(
                tableState,
                playerId,
                flower,
                listOf(Step.FlowerRevealed(playerId, flower, replacementTile = null)),
            )
        val completedStep = Step.FlowerRevealed(playerId, flower, supplemented.replacementTile)
        val afterReplacement = TaiwanSpecialFlowerWin.findOpportunity(supplemented.tableState, playerId, flower)
        if (afterReplacement?.type == TaiwanTaiType.EIGHT_FLOWERS) {
            return Result.AwaitingDecision(
                Pending(
                    supplemented.tableState,
                    afterReplacement,
                    listOf(playerId),
                    0,
                    playerId,
                    flower,
                    replacementCompleted = true,
                ),
                listOf(completedStep),
            )
        }
        return process(supplemented.tableState, listOf(playerId)).withLeadingStep(completedStep)
    }

    /** 在接續流程的結果前補上先成立的牌區變化。 */
    private fun Result.withLeadingStep(step: Step): Result = when (this) {
        is Result.Completed -> copy(steps = listOf(step) + steps)
        is Result.Exhausted -> copy(steps = listOf(step) + steps)
        is Result.AwaitingDecision -> copy(steps = listOf(step) + steps)
        is Result.Won -> error("A flower replacement chain does not complete a special flower win")
    }

    /**
     * 套用一次特殊花胡選擇。
     *
     * @param pending 等待中的花胡資格。
     * @param accepted 玩家是否接受花胡。
     * @return 接受時的花胡結果，或放棄後繼續補花所得結果。
     */
    fun resolve(pending: Pending, accepted: Boolean): Result {
        if (!accepted) {
            if (pending.replacementCompleted) {
                return process(pending.tableState, pending.playerOrderIds, pending.playerOrderIndex)
            }
            val supplemented = TaiwanFlowerReplacement.supplement(pending.tableState, pending.revealingPlayerId)
                ?: return Result.Exhausted(
                    pending.tableState,
                    pending.revealingPlayerId,
                    pending.revealedFlower,
                    emptyList(),
                )
            val drawn = Step.ReplacementDrawn(pending.revealingPlayerId, supplemented.replacementTile)
            return process(supplemented.tableState, pending.playerOrderIds, pending.playerOrderIndex).withLeadingStep(drawn)
        }
        return Result.Won(
            tableState = pending.tableState,
            opportunity = pending.opportunity,
            settlement = TaiwanSpecialFlowerWin.settle(pending.tableState, pending.opportunity),
            winningFlower = pending.opportunity.claimedFlower ?: pending.revealedFlower,
        )
    }
}
