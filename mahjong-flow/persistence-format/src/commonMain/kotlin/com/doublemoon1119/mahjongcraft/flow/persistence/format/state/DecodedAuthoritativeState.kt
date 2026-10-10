package com.doublemoon1119.mahjongcraft.flow.persistence.format.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import kotlin.uuid.Uuid

/**
 * 解碼並驗證後的伺服器權威狀態。
 *
 * @property rooms 以場地 UUID 索引的等待階段狀態。
 * @property games 以場地 UUID 索引的進行中狀態。
 * @property historyRecordingState 待寫歷史與重試 checkpoint。
 */
data class DecodedAuthoritativeState(
    val rooms: Map<Uuid, Room>,
    val games: Map<Uuid, Game>,
    val historyRecordingState: HistoryRecordingState = HistoryRecordingState(),
)
