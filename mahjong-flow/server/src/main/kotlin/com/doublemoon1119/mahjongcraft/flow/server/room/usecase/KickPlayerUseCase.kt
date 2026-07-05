package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomNotificationService
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.logic.room.toSnapshot
import java.util.*

/**
 * 處理房主剔除成員流程的應用層用例。
 *
 * 驗證房主權限後移除目標玩家，並明確標記移除原因為被剔除。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property notificationService 房間通知服務。
 */
class KickPlayerUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    private val notificationService: RoomNotificationService
) {
    /**
     * 執行剔除玩家邏輯。
     *
     * @param roomId 房間 UUID。
     * @param operatorId 發起剔除請求的玩家 UUID。
     * @param targetPlayerId 被剔除的目標玩家 UUID。
     * @return 剔除結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: UUID,
        operatorId: UUID,
        targetPlayerId: UUID
    ): Outcome<Unit, RoomError> {
        val room = roomRepository.getRoom(roomId)
            ?: return Outcome.Error(RoomError.RoomNotFound(roomId))

        // 1. 驗證發起者是否為房主
        if (operatorId != room.hostId) {
            return Outcome.Error(RoomError.NotHost(operatorId))
        }

        // 2. 驗證目標是否在房間內且不是房主本人
        if (!room.playerIds.contains(targetPlayerId)) {
            return Outcome.Error(RoomError.PlayerNotInRoom(targetPlayerId, roomId))
        }
        if (targetPlayerId == room.hostId) {
            return Outcome.Error(RoomError.HostCannotKickSelf(targetPlayerId))
        }

        // 3. 更新房間資料並持久化
        val updatedRoom = room.copy(
            playerIds = room.playerIds - targetPlayerId,
            readyPlayerIds = room.readyPlayerIds - targetPlayerId
        )
        roomRepository.setRoom(updatedRoom)

        // 4. 同步新狀態給所有觀察者
        val observers = snapshotRepository.getAllObservers(roomId)
        observers.forEach { observerId ->
            snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
        }

        // 5. 通知**原房間**內的所有玩家
        room.playerIds.forEach { memberId ->
            notificationService.notifyLeave(
                roomId = roomId,
                targetPlayerId = memberId,
                leftPlayerId = targetPlayerId,
                reason = LeaveReason.Kicked
            )
        }

        return Outcome.Success(Unit)
    }
}