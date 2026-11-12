package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.PlayerDraw
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileReplacementRounds
import kotlin.uuid.Uuid

/**
 * 以 [TileReplacementRounds] 逐輪處理台灣麻將補花，並在特殊花胡成立時暫停等待選擇。
 *
 * 局首補花由莊家開始依序，每位玩家把手上的花一次全部公開、補同樣張數；補到的花等這一輪結束後的下一輪再補。
 * 牌局中摸到花時只有摸牌者一人，補到的花當場接著補。七搶一在公開花之後、補牌之前開放選擇；八仙過海在本人補完牌
 * 之後開放選擇。沒有牌可以補時，花公開後不開放七搶一，本局流局。
 *
 * 本物件不保存可變狀態；[Pending] 帶齊恢復流程所需的進度，重新載入後可以用同一份資料繼續。
 */
object TaiwanFlowerFlow {
    /** 一次補花流程的結果。 */
    sealed interface Result {
        /**
         * 所有指定玩家都已沒有待補花牌。
         *
         * @property tableState 補花完成的桌況。
         * @property steps 這次處理的牌區變化。
         */
        data class Completed(
            val tableState: TableState,
            val steps: List<TileReplacementRounds.Step>,
        ) : Result

        /**
         * 需要補牌時已經沒有牌可以補，本局應流局。
         *
         * @property tableState 停在無法補牌時的桌況。
         * @property playerId 無法補牌的玩家。
         * @property steps 這次處理的牌區變化。
         */
        data class Exhausted(
            val tableState: TableState,
            val playerId: Uuid,
            val steps: List<TileReplacementRounds.Step>,
        ) : Result

        /**
         * 特殊花胡成立，必須等待資格玩家接受或放棄。
         *
         * @property pending 等待中的花胡選擇。
         * @property steps 這次處理的牌區變化。
         */
        data class AwaitingDecision(
            val pending: Pending,
            val steps: List<TileReplacementRounds.Step>,
        ) : Result

        /**
         * 玩家接受特殊花胡，呼叫端應套用付款並結束本局。
         *
         * @property tableState 花胡成立時的桌況。
         * @property opportunity 接受的花胡資格。
         * @property settlement 花胡付款。
         * @property winningFlower 讓花胡成立的花牌。
         */
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
     * @property tableState 暫停時的桌況；八仙過海也已完成本次補牌。
     * @property opportunity 本次唯一的花胡資格。
     * @property playerOrderIds 每一輪補花的玩家順序；牌局中摸到花時只有摸牌者。
     * @property position 放棄花胡後繼續補花的位置。
     * @property revealingPlayerId 本次公開花牌的玩家。
     * @property revealedFlower 讓花胡成立的花牌。
     */
    data class Pending(
        val tableState: TableState,
        val opportunity: TaiwanSpecialFlowerWin.Opportunity,
        val playerOrderIds: List<Uuid>,
        val position: TileReplacementRounds.Position,
        val revealingPlayerId: Uuid,
        val revealedFlower: IdentifiedTile,
    )

    /**
     * 從 [from] 開始逐輪補花。
     *
     * @param tableState 補花前的桌況。
     * @param playerOrderIds 每一輪的玩家順序；局首由莊家開始，牌局中只放入摸牌者。
     * @param from 開始或繼續的位置。
     * @return 完成、耗盡或等待花胡選擇的結果。
     */
    fun process(
        tableState: TableState,
        playerOrderIds: List<Uuid>,
        from: TileReplacementRounds.Position = TileReplacementRounds.Position.START,
    ): Result = when (val outcome = TileReplacementRounds.process(tableState, playerOrderIds, FlowerRules, from)) {
        is TileReplacementRounds.Outcome.Completed -> Result.Completed(outcome.tableState, outcome.steps)
        is TileReplacementRounds.Outcome.Exhausted -> Result.Exhausted(outcome.tableState, outcome.playerId, outcome.steps)
        is TileReplacementRounds.Outcome.Paused -> Result.AwaitingDecision(
            pending = outcome.reason.pending(outcome.tableState, playerOrderIds, outcome.position),
            steps = outcome.steps,
        )
    }

    /**
     * 接續一張牌局中摸到、已移到公開花區、尚未補牌的花牌。
     *
     * @param tableState 花牌已公開的桌況。
     * @param playerId 公開花牌的玩家。
     * @param flower 已公開的花牌。
     * @return 花胡選擇、補牌耗盡或完成後續連續補花的結果；牌區變化從這張花的補牌開始，不含摸牌時已公開的這張花。
     */
    fun processRevealed(
        tableState: TableState,
        playerId: Uuid,
        flower: IdentifiedTile,
    ): Result {
        val order = listOf(playerId)
        val resumeAt = TileReplacementRounds.Position(playerOrderIndex = 0, replacementsDue = 1)
        FlowerRules.pauseAfterSetAside(tableState, playerId, listOf(flower))?.let { pause ->
            return Result.AwaitingDecision(pause.pending(tableState, order, resumeAt), emptyList())
        }
        return process(tableState, order, resumeAt)
    }

    /**
     * 套用一次特殊花胡選擇。
     *
     * @param pending 等待中的花胡資格。
     * @param accepted 玩家是否接受花胡。
     * @return 接受時的花胡結果，或放棄後繼續補花所得結果。
     */
    fun resolve(pending: Pending, accepted: Boolean): Result {
        if (!accepted) return process(pending.tableState, pending.playerOrderIds, pending.position)
        return Result.Won(
            tableState = pending.tableState,
            opportunity = pending.opportunity,
            settlement = TaiwanSpecialFlowerWin.settle(pending.tableState, pending.opportunity),
            winningFlower = pending.opportunity.claimedFlower ?: pending.revealedFlower,
        )
    }

    /**
     * 補花時成立的特殊花胡。
     *
     * @property opportunity 花胡資格。
     * @property revealingPlayerId 公開花牌的玩家。
     * @property flower 讓花胡成立的花牌。
     */
    private data class FlowerPause(
        val opportunity: TaiwanSpecialFlowerWin.Opportunity,
        val revealingPlayerId: Uuid,
        val flower: IdentifiedTile,
    ) {
        /**
         * 建立可恢復的等待選擇。
         *
         * @param tableState 暫停時的桌況。
         * @param playerOrderIds 每一輪的玩家順序。
         * @param position 放棄後繼續的位置。
         * @return 等待中的花胡選擇。
         */
        fun pending(
            tableState: TableState,
            playerOrderIds: List<Uuid>,
            position: TileReplacementRounds.Position,
        ): Pending = Pending(tableState, opportunity, playerOrderIds, position, revealingPlayerId, flower)
    }

    /** 台灣麻將補花的移出、補牌與花胡暫停方式。 */
    private object FlowerRules : TileReplacementRounds.Rules<FlowerPause> {
        override fun isSetAside(tile: IdentifiedTile): Boolean = TaiwanTileTypes.isFlower(tile.tile)

        override fun setAside(tableState: TableState, playerId: Uuid, tile: IdentifiedTile): TableState = requireNotNull(TaiwanFlowerReplacement.reveal(tableState, playerId, tile)) { "Selected flower could not be revealed" }

        override fun replace(
            tableState: TableState,
            playerId: Uuid,
            replacementsRemaining: Int,
        ): PlayerDraw? = TaiwanFlowerReplacement.supplement(tableState, playerId, replacementsRemaining)?.let {
            PlayerDraw(it.tableState, it.replacementTile)
        }

        /** 還有牌可以補時，公開的花讓有人湊到七搶一就暫停。 */
        override fun pauseAfterSetAside(tableState: TableState, playerId: Uuid, tiles: List<IdentifiedTile>): FlowerPause? {
            if (tableState.tileWall.remainingCount == 0) return null
            return tiles.firstNotNullOfOrNull { flower ->
                TaiwanSpecialFlowerWin.findOpportunity(tableState, playerId, flower)
                    ?.takeIf { it.type == TaiwanTaiType.SEVEN_FLOWERS }
                    ?.let { FlowerPause(it, playerId, flower) }
            }
        }

        /** 補完牌後本人湊齊八張花就暫停。 */
        override fun pauseAfterReplacement(tableState: TableState, playerId: Uuid): FlowerPause? {
            val flower = tableState.players.first { it.id == playerId }.tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles?.lastOrNull() ?: return null
            return TaiwanSpecialFlowerWin.findOpportunity(tableState, playerId, flower)
                ?.takeIf { it.type == TaiwanTaiType.EIGHT_FLOWERS }
                ?.let { FlowerPause(it, playerId, flower) }
        }
    }
}
