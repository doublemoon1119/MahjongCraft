package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.SyncRoomSnapshotUseCase
import kotlin.uuid.Uuid

/**
 * 依權威房間重新產生每位觀察者的快照並送出。
 *
 * 供不伴隨房間事件、但會改變房間快照內容的動作使用，觀察者包含房間成員與只開啟房間畫面的玩家。
 *
 * @param tableId 房間所屬的麻將桌 ID。
 * @param snapshots 記錄觀察者的房間快照倉庫。
 * @param syncRoom 依權威房間寫入觀察者快照的用例。
 * @param send 送出指定觀察者目前快照的動作。
 */
internal suspend fun syncRoomToObservers(
    tableId: Uuid,
    snapshots: RoomSnapshotRepository,
    syncRoom: SyncRoomSnapshotUseCase,
    send: suspend (observerId: Uuid) -> Unit,
) {
    snapshots.getAllObservers(tableId).forEach { observerId ->
        if (syncRoom(tableId, observerId) is Outcome.Success) send(observerId)
    }
}
