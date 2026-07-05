package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.result.Outcome
import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.application.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.application.common.room.service.RoomNotificationService
import com.doublemoon1119.mahjongcraft.application.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import java.util.*

/**
 * 玩家離開房間的應用層用例。
 *
 * 負責處理玩家主動退出或斷線時的房間狀態更新。若房主離開，則執行房間解散邏輯。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property notificationService 房間通知服務。
 */
class LeaveRoomUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    private val notificationService: RoomNotificationService
) {
    /**
     * 執行離開房間邏輯。
     *
     * @param roomId 房間 UUID。
     * @param playerId 欲離開的玩家 UUID。
     * @return 離開房間的結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: UUID,
        playerId: UUID
    ): Outcome<Unit, RoomError> {
        val room = roomRepository.getRoom(roomId)
            ?: return Outcome.Error(RoomError.RoomNotFound(roomId))

        // 1. 若離開者為房主，則解散房間
        if (playerId == room.hostId) {
            roomRepository.removeRoom(roomId)
            room.playerIds.forEach { memberId ->
                // 通知房間內的所有玩家，並移除快照
                notificationService.notifyLeave(
                    roomId = roomId,
                    targetPlayerId = memberId,
                    leftPlayerId = memberId,
                    reason = LeaveReason.Dissolved
                )
                snapshotRepository.removeSnapshot(roomId, memberId)
            }
            return Outcome.Success(Unit)
        }

        // 2. 若為普通玩家，更新成員清單與準備狀態
        val updatedRoom = room.copy(
            playerIds = room.playerIds - playerId,
            readyPlayerIds = room.readyPlayerIds - playerId
        )

        // 3. 儲存更新後的狀態
        roomRepository.setRoom(updatedRoom)

        // 4. 同步最新快照給所有觀察者
        val observers = snapshotRepository.getAllObservers(roomId)
        observers.forEach { observerId ->
            snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
        }

        // 5. 通知**原房間**內的所有玩家
        room.playerIds.forEach { memberId ->
            notificationService.notifyLeave(
                roomId = roomId,
                targetPlayerId = memberId,
                leftPlayerId = playerId,
                reason = LeaveReason.Voluntary
            )
        }

        return Outcome.Success(Unit)
    }
}