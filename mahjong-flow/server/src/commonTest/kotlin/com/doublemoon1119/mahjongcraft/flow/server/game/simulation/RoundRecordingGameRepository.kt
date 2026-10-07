package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 一局的開局桌況與目前為止最後一次寫入的桌況。
 *
 * @property start 這一局第一次寫入的權威桌況；從它重新推進可以重播整局。
 * @property final 這一局最後一次寫入的權威桌況；換局後即為這一局結束時的桌況。
 */
internal class RecordedRound(val start: TableState) {
    var final: TableState = start
        internal set
}

/**
 * 記錄每一局開局與最終桌況的 [GameRepository]。
 *
 * 每次寫入後比較局位、本場數與莊家：與目前這一局不同時視為新的一局開始，否則更新目前這一局的最終桌況。
 *
 * @property delegate 實際保存狀態的倉庫。
 */
internal class RoundRecordingGameRepository(
    private val delegate: FakeGameRepository = FakeGameRepository(),
) : GameRepository by delegate {
    /**
     * 區分不同局的鍵。
     *
     * @property position 局位。
     * @property comboCount 本場數。
     * @property dealerPlayerId 莊家。
     */
    private data class RoundKey(
        val position: MatchRoundPosition,
        val comboCount: Int,
        val dealerPlayerId: Uuid,
    )

    /** 每場對局依序記錄的局。 */
    private val roundsByGame = mutableMapOf<Uuid, MutableList<Pair<RoundKey, RecordedRound>>>()

    /** 目前為止寫入的歷史事件草稿，依寫入順序排列。 */
    val historyDrafts: List<HistoryEventDraft> get() = delegate.historyDrafts

    /** [gameId] 目前為止記錄的所有局，依開局順序排列。 */
    fun rounds(gameId: Uuid): List<RecordedRound> = roundsByGame[gameId].orEmpty().map { it.second }

    override suspend fun setTableState(state: TableState) {
        delegate.setTableState(state)
        observe(state.id)
    }

    /** 寫入 [state] 並指定由 AI 操控的玩家。 */
    suspend fun setTableState(state: TableState, aiPlayerStrategyKeys: Map<Uuid, String>) {
        delegate.setTableState(state, aiPlayerStrategyKeys)
        observe(state.id)
    }

    override suspend fun <T> updateGame(
        gameId: Uuid,
        history: (Game?, Game?, T) -> List<HistoryEventDraft>,
        block: suspend (Game?) -> Pair<Game?, T>,
    ): T = delegate.updateGame(gameId, history, block).also { observe(gameId) }

    override suspend fun <T> update(
        gameId: Uuid,
        history: (TableState?, TableState?, T) -> List<HistoryEventDraft>,
        block: suspend (TableState?) -> Pair<TableState?, T>,
    ): T = delegate.update(gameId, history, block).also { observe(gameId) }

    /** 依最新的桌況更新目前這一局，或記錄新的一局。 */
    private suspend fun observe(gameId: Uuid) {
        val state = delegate.getTableState(gameId) ?: return
        val key = RoundKey(position = state.roundPosition, comboCount = state.comboCount, dealerPlayerId = state.dealerPlayerId)
        val rounds = roundsByGame.getOrPut(gameId) { mutableListOf() }
        val current = rounds.lastOrNull()
        if (current == null || current.first != key) {
            rounds += key to RecordedRound(state)
        } else {
            current.second.final = state
        }
    }
}
