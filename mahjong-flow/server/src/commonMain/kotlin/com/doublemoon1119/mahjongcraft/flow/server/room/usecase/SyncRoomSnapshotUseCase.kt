package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import kotlin.uuid.Uuid

/**
 * 同步房間快照的應用層用例。
 *
 * 負責處理特定觀察者的同步請求，從權威數據倉庫獲取房間狀態，並針對請求者的身分生成對應的視角快照。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 */
class SyncRoomSnapshotUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository
) {
    /**
     * 執行房間狀態的同步處理。
     *
     * 根據房間識別碼尋找權威實體，若存在則產出針對指定觀察者的快照並存入快照倉庫。
     *
     * @param roomId 欲同步的房間 Uuid。
     * @param observerId 需要更新快照的觀察者 Uuid。
     * @return 同步快照的結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        observerId: Uuid
    ): Outcome<Unit, RoomError> {
        val room = roomRepository.getRoom(roomId)
            ?: return Outcome.Error(RoomError.RoomNotFound(roomId))

        // 生成針對該觀察者身分的快照（例如：區分房主與非房主視角）
        val snapshot = room.toSnapshot(observerId)

        // 觸發快照倉庫的更新，進而通知客戶端
        snapshotRepository.setSnapshot(observerId, snapshot)

        return Outcome.Success(Unit)
    }
}