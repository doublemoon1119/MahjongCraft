package com.doublemoon1119.mahjongcraft.flow.persistence.format.state

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.GameFlowConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.GameRuntimeStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TableStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.room.RoomPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.module.requireValidAutomaticControlIds
import kotlinx.serialization.Serializable

/**
 * 同批保存所有 [Room] 與 [TableState] 的伺服器權威狀態 DTO。
 *
 * @property rooms 以 Room UUID 字串索引的等待階段狀態。
 * @property games 以 Game UUID 字串索引的進行中狀態。
 * @property gameFlowConfigs 以 Game UUID 字串索引的流程與觀看設定。
 * @property gameRuntimeStates 以 Game UUID 字串索引的流程 runtime 狀態。
 * @property historyRecordingState 與 Game 移除相互獨立的待寫歷史與 checkpoint。
 */
@Serializable
data class AuthoritativeStatePersistenceDto(
    val rooms: Map<String, RoomPersistenceDto>,
    val games: Map<String, TableStatePersistenceDto>,
    val gameFlowConfigs: Map<String, GameFlowConfigPersistenceDto>,
    val gameRuntimeStates: Map<String, GameRuntimeStatePersistenceDto>,
    val historyRecordingState: HistoryRecordingPersistenceDto,
) {
    init {
        require(rooms.all { (id, room) -> id == room.id }) { "Room persistence index must match its DTO ID" }
        require(games.all { (id, game) -> id == game.id }) { "Game persistence index must match its DTO ID" }
        require(games.keys == gameFlowConfigs.keys) { "Every game must have exactly one flow config" }
        require(games.keys == gameRuntimeStates.keys) { "Every game must have exactly one runtime state" }
        games.forEach { (gameId, game) ->
            val playerIds = game.players.mapTo(mutableSetOf()) { it.id }
            require(gameRuntimeStates.getValue(gameId).remainingReserveMillisByPlayerId.keys == playerIds) {
                "Game runtime state must contain exactly the game players"
            }
            require(gameRuntimeStates.getValue(gameId).remainingReserveMillisByPlayerId.values.all { it >= 0L }) {
                "Remaining reserve time must not be negative"
            }
            require(gameRuntimeStates.getValue(gameId).forcedAutoPlayPlayerIds.all { it in playerIds }) {
                "Forced auto-play players must belong to the game"
            }
            require(gameRuntimeStates.getValue(gameId).aiPlayerStrategyKeys.keys.all { it in playerIds }) {
                "AI players must belong to the game"
            }
            require(gameRuntimeStates.getValue(gameId).enabledAutomaticControlIdsByPlayerId.keys.all { it in playerIds }) {
                "Automatic control players must belong to the game"
            }
            gameRuntimeStates.getValue(gameId).enabledAutomaticControlIdsByPlayerId.values.forEach(::requireValidAutomaticControlIds)
        }
        require(rooms.keys.intersect(games.keys).isEmpty()) {
            "The same venue ID must not exist as both a room and a game"
        }
    }
}
