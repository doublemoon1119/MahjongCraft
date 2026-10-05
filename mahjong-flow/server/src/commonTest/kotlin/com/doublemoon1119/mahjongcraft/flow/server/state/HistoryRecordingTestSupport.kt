package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 以正式開局事實建立一場對局，讓記錄政策決定它的歷史資格；開局事實占用該場序號 1。
 *
 * @param tableState 新對局的開局桌況。
 * @return 已寫入權威狀態的對局。
 */
internal suspend fun AuthoritativeStateStore.openGame(tableState: TableState): Game {
    val game = Game(tableState, GameFlowConfig())
    update { state ->
        AuthoritativeStateUpdate(
            state = state.copy(games = state.games + (game.id to game)),
            result = Unit,
            historyDraftsByVenueId = mapOf(
                game.id to listOf(HistoryEventDraft(null, HistoryFact.MatchStarted(tableState, game.flowConfig, emptyMap()))),
            ),
        )
    }
    return game
}
