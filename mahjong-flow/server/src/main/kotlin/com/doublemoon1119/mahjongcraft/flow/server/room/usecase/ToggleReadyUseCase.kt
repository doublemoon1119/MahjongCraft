package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomNotificationService
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.logic.room.toSnapshot
import java.util.*

/**
 * 切換玩家準備狀態的應用層用例。
 *
 * 負責處理房間內非房主玩家的準備狀態變更。變更後會向所有相關觀察者同步最新的房間快照。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property notificationService 房間通知服務。
 */
class ToggleReadyUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    private val notificationService: RoomNotificationService
) {
    /**
     * 執行準備狀態切換邏輯。
     *
     * @param roomId 房間 UUID。
     * @param playerId 發起請求的玩家 UUID。
     * @return 切換準備狀態的結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: UUID,
        playerId: UUID
    ): Outcome<Unit, RoomError> {
        val room = roomRepository.getRoom(roomId)
            ?: return Outcome.Error(RoomError.RoomNotFound(roomId))

        // 1. 驗證玩家是否在房間內
        if (!room.playerIds.contains(playerId)) {
            return Outcome.Error(RoomError.PlayerNotInRoom(playerId, roomId))
        }

        // 2. 房主不參與準備狀態切換（業務邏輯限制）
        if (playerId == room.hostId) {
            return Outcome.Success(Unit)
        }

        // 3. 計算新的準備狀態集合
        val isNowReady = !room.readyPlayerIds.contains(playerId)
        val newReadyPlayerIds = if (isNowReady) {
            room.readyPlayerIds + playerId
        } else {
            room.readyPlayerIds - playerId
        }

        // 4. 更新領域模型並持久化
        val updatedRoom = room.copy(readyPlayerIds = newReadyPlayerIds)
        roomRepository.setRoom(updatedRoom)

        // 5. 向所有觀察者同步更新後的狀態
        val observers = snapshotRepository.getAllObservers(roomId)
        observers.forEach { observerId ->
            snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
        }

        // 6. 通知房間內的所有成員
        updatedRoom.playerIds.forEach { memberId ->
            notificationService.notifyReady(
                roomId = roomId,
                targetPlayerId = memberId,
                readyPlayerId = playerId,
                isReady = isNowReady
            )
        }

        return Outcome.Success(Unit)
    }
}