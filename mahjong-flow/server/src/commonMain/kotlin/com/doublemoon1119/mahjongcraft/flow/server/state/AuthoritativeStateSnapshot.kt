package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import kotlin.uuid.Uuid

/**
 * 伺服器目前持有的完整 Room 與 Game 狀態快照。
 *
 * @property rooms 以場地 UUID 索引的等待階段狀態。
 * @property games 以場地 UUID 索引的進行中狀態。
 * @property historyRecordingState 與 Game 生命週期分離的待寫歷史事件。
 */
data class AuthoritativeStateSnapshot(
    val rooms: Map<Uuid, Room> = emptyMap(),
    val games: Map<Uuid, Game> = emptyMap(),
    val historyRecordingState: HistoryRecordingState = HistoryRecordingState(),
) {
    init {
        require(rooms.all { (id, room) -> id == room.id }) { "Room index must match its state ID" }
        require(games.all { (id, game) -> id == game.id }) { "Game index must match its state ID" }
        require(rooms.keys.intersect(games.keys).isEmpty()) {
            "The same venue ID must not exist as both a room and a game"
        }
    }
}
