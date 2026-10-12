package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.table.TableState
/**
 * 驗證並將 [WinRoundDirective.ContinueRound] 套用到 [state]，回傳套用後的新 [TableState]。
 *
 * @param state 套用此延續決策前的桌況。
 * @return 套用新完成玩家及下一位行動玩家後的桌況。
 * @throws IllegalArgumentException [newlyFinishedPlayerIds] 內含不屬於這場對局、或已經是 finished 的玩家；
 * 或套用後將導致所有玩家皆 finished（resolver 遇到這種終止條件應改回傳 [WinRoundDirective.EndRound]）；
 * 或 [nextPlayerId] 不屬於這場對局、或套用後仍是 finished。
 */
fun WinRoundDirective.ContinueRound.applyTo(state: TableState): TableState {
    val playerIds = state.players.mapTo(mutableSetOf()) { it.id }
    require(newlyFinishedPlayerIds.all { it in playerIds }) {
        "newlyFinishedPlayerIds must belong to this table: $newlyFinishedPlayerIds"
    }
    require(newlyFinishedPlayerIds.none { it in state.finishedPlayerIds }) {
        "newlyFinishedPlayerIds must not already be finished: $newlyFinishedPlayerIds"
    }
    val updatedFinishedPlayerIds = state.finishedPlayerIds + newlyFinishedPlayerIds
    require(updatedFinishedPlayerIds.size < state.playerCount) {
        "ContinueRound must leave at least one active player; return EndRound instead once the end condition is met"
    }
    require(nextPlayerId in playerIds && nextPlayerId !in updatedFinishedPlayerIds) {
        "nextPlayerId must be an active player on this table: $nextPlayerId"
    }
    return state.copy(
        finishedPlayerIds = updatedFinishedPlayerIds,
        currentPlayerIndex = state.players.indexOfFirst { it.id == nextPlayerId },
    )
}
