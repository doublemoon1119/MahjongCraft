package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import kotlin.uuid.Uuid

/**
 * 逐輪把手牌中規則指定的牌移出並補牌，例如使用花牌的規則在開局時補花。
 *
 * 每一輪依 [process] 的玩家順序，讓每位玩家把手上所有指定的牌一次移出，再補同樣張數；補到的牌又是指定的牌時，
 * 等這一輪所有玩家都處理完，下一輪再處理。一直重複到沒有玩家手上有指定的牌。規則可以在移出後或補牌後暫停，
 * 例如讓玩家選擇是否以這些牌和牌；暫停時回報的 [Position] 帶齊繼續所需的進度。
 *
 * 本物件不保存可變狀態。
 */
object TileReplacementRounds {
    /**
     * 繼續處理的位置。
     *
     * @property playerOrderIndex 目前處理到的玩家在玩家順序中的索引。
     * @property replacementsDue 這位玩家已移出、尚未補的張數；為 0 時從移出開始處理這位玩家。
     * @property round 目前所在的輪次，從 1 開始。
     */
    data class Position(
        val playerOrderIndex: Int,
        val replacementsDue: Int,
        val round: Int,
    ) {
        init {
            require(playerOrderIndex >= 0) { "Player order index must not be negative" }
            require(replacementsDue >= 0) { "Replacements due must not be negative" }
            require(round >= 1) { "Replacement round must start at one" }
        }

        /** [Position] 的固定值。 */
        companion object {
            /** 第一輪從第一位玩家開始。 */
            val START: Position = Position(playerOrderIndex = 0, replacementsDue = 0, round = 1)
        }
    }

    /** 一次已成立的牌區變化，依發生順序排列。 */
    sealed interface Step {
        /** 牌區變化所屬的玩家。 */
        val playerId: Uuid

        /** 這次變化之後的桌況。 */
        val tableState: TableState

        /** 這次移出或補到的牌，依發生順序排列。 */
        val tiles: List<IdentifiedTile>

        /** 這次變化所在的輪次，從 1 開始。 */
        val round: Int

        /**
         * 玩家把手上的指定牌一次移出。
         *
         * @property playerId 移出牌的玩家。
         * @property tiles 移出的牌，依移出順序排列。
         * @property tableState 移出之後的桌況。
         * @property round 移出所在的輪次。
         */
        data class SetAside(
            override val playerId: Uuid,
            override val tiles: List<IdentifiedTile>,
            override val tableState: TableState,
            override val round: Int,
        ) : Step

        /**
         * 玩家補入移出的張數。
         *
         * @property playerId 補牌的玩家。
         * @property tiles 補到的牌，依補牌順序排列。
         * @property tableState 補牌之後的桌況。
         * @property round 補牌所在的輪次。
         */
        data class Replaced(
            override val playerId: Uuid,
            override val tiles: List<IdentifiedTile>,
            override val tableState: TableState,
            override val round: Int,
        ) : Step
    }

    /**
     * 一次處理的結果。
     *
     * @param P 規則暫停時附帶的資料型別。
     */
    sealed interface Outcome<out P : Any> {
        /** 這次處理依序成立的牌區變化。 */
        val steps: List<Step>

        /**
         * 沒有玩家手上還有指定的牌。
         *
         * @property tableState 處理完成的桌況。
         * @property steps 這次處理的牌區變化。
         */
        data class Completed(
            val tableState: TableState,
            override val steps: List<Step>,
        ) : Outcome<Nothing>

        /**
         * 需要補牌時已經沒有牌可以補。
         *
         * @property tableState 停在無法補牌時的桌況。
         * @property playerId 無法補牌的玩家。
         * @property steps 這次處理的牌區變化。
         */
        data class Exhausted(
            val tableState: TableState,
            val playerId: Uuid,
            override val steps: List<Step>,
        ) : Outcome<Nothing>

        /**
         * 規則要求暫停。
         *
         * @property tableState 暫停時的桌況。
         * @property position 繼續處理的位置。
         * @property reason 規則提供的暫停原因。
         * @property steps 這次處理的牌區變化。
         */
        data class Paused<P : Any>(
            val tableState: TableState,
            val position: Position,
            val reason: P,
            override val steps: List<Step>,
        ) : Outcome<P>
    }

    /**
     * 規則提供的移出與補牌方式。
     *
     * @param P 規則暫停時附帶的資料型別。
     */
    interface Rules<P : Any> {
        /**
         * [tile] 是否要移出。
         *
         * @param tile 玩家手上的牌。
         * @return 要移出時為 true。
         */
        fun isSetAside(tile: IdentifiedTile): Boolean

        /**
         * 把 [tile] 從 [playerId] 的手牌移到規則的移出區。
         *
         * @param tableState 移出前的桌況。
         * @param playerId 移出牌的玩家。
         * @param tile 要移出的牌。
         * @return 移出後的桌況。
         */
        fun setAside(tableState: TableState, playerId: Uuid, tile: IdentifiedTile): TableState

        /**
         * 為 [playerId] 補一張牌。
         *
         * @param tableState 補牌前的桌況。
         * @param playerId 補牌的玩家。
         * @param replacementsRemaining 包含本次在內，這位玩家尚待補入的張數。
         * @return 補牌後的桌況與補到的牌；已經沒有牌可以補時為 null。
         */
        fun replace(
            tableState: TableState,
            playerId: Uuid,
            replacementsRemaining: Int,
        ): PlayerDraw?

        /**
         * 玩家移出 [tiles] 之後、補牌之前是否暫停；預設不暫停。
         *
         * @param tableState 移出後的桌況。
         * @param playerId 移出牌的玩家。
         * @param tiles 這次移出的牌。
         * @return 暫停原因；不暫停時為 null。
         */
        fun pauseAfterSetAside(tableState: TableState, playerId: Uuid, tiles: List<IdentifiedTile>): P? = null

        /**
         * 玩家補完這次的張數之後是否暫停；預設不暫停。
         *
         * @param tableState 補牌後的桌況。
         * @param playerId 補牌的玩家。
         * @return 暫停原因；不暫停時為 null。
         */
        fun pauseAfterReplacement(tableState: TableState, playerId: Uuid): P? = null
    }

    /**
     * 從 [from] 開始逐輪處理。
     *
     * @param tableState 處理前的桌況。
     * @param playerOrderIds 每一輪的玩家順序，不可重複。
     * @param rules 規則提供的移出與補牌方式。
     * @param from 開始或繼續的位置。
     * @return 完成、無牌可補或暫停的結果。
     * @throws IllegalArgumentException 玩家順序重複、包含不在桌上的玩家，或位置超出玩家順序時拋出。
     */
    fun <P : Any> process(
        tableState: TableState,
        playerOrderIds: List<Uuid>,
        rules: Rules<P>,
        from: Position = Position.START,
    ): Outcome<P> {
        require(playerOrderIds.distinct().size == playerOrderIds.size) { "Replacement player order must be unique" }
        require(playerOrderIds.all { id -> tableState.players.any { it.id == id } }) { "Replacement players must belong to the table" }
        require(from.playerOrderIndex < playerOrderIds.size) { "Replacement position is outside the player order" }
        var state = tableState
        var position = from
        val steps = mutableListOf<Step>()
        while (true) {
            while (position.playerOrderIndex < playerOrderIds.size) {
                val playerId = playerOrderIds[position.playerOrderIndex]
                var due = position.replacementsDue
                if (due == 0) {
                    val tiles = state.players.first { it.id == playerId }.hand.standingTiles.filter(rules::isSetAside)
                    if (tiles.isNotEmpty()) {
                        tiles.forEach { tile -> state = rules.setAside(state, playerId, tile) }
                        steps += Step.SetAside(playerId, tiles, state, position.round)
                        due = tiles.size
                        rules.pauseAfterSetAside(state, playerId, tiles)?.let { reason ->
                            return Outcome.Paused(state, Position(position.playerOrderIndex, due, position.round), reason, steps.toList())
                        }
                    }
                }
                if (due > 0) {
                    val replacements = mutableListOf<IdentifiedTile>()
                    repeat(due) { replacementIndex ->
                        val replaced = rules.replace(state, playerId, due - replacementIndex)
                        if (replaced == null) {
                            if (replacements.isNotEmpty()) steps += Step.Replaced(playerId, replacements.toList(), state, position.round)
                            return Outcome.Exhausted(state, playerId, steps.toList())
                        }
                        state = replaced.tableState
                        replacements += replaced.tile
                    }
                    steps += Step.Replaced(playerId, replacements.toList(), state, position.round)
                    rules.pauseAfterReplacement(state, playerId)?.let { reason ->
                        return Outcome.Paused(state, Position(position.playerOrderIndex + 1, 0, position.round).wrapped(playerOrderIds.size), reason, steps.toList())
                    }
                }
                position = Position(position.playerOrderIndex + 1, 0, position.round)
            }
            val anyRemaining = playerOrderIds.any { id -> state.players.first { it.id == id }.hand.standingTiles.any(rules::isSetAside) }
            if (!anyRemaining) return Outcome.Completed(state, steps.toList())
            position = Position(playerOrderIndex = 0, replacementsDue = 0, round = position.round + 1)
        }
    }

    /**
     * 超過最後一位玩家時回到下一輪的第一位。
     *
     * @param playerCount 玩家順序的人數。
     * @return 位於玩家順序內的位置。
     */
    private fun Position.wrapped(playerCount: Int): Position = if (playerOrderIndex >= playerCount) Position(playerOrderIndex = 0, replacementsDue = 0, round = round + 1) else this
}
