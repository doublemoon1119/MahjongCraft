package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomNotificationService
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import java.util.*

/**
 * 玩家加入房間的應用層用例。
 *
 * 負責處理玩家請求進入特定房間的邏輯，包含房間狀態檢查、人數限制驗證以及全體成員的狀態同步。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property notificationService 房間通知服務。
 */
class JoinRoomUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    private val notificationService: RoomNotificationService
) {
    /**
     * 執行加入房間邏輯。
     *
     * @param roomId 欲加入的房間 UUID。
     * @param playerId 請求加入的玩家 UUID。
     * @return 加入結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: UUID,
        playerId: UUID
    ): Outcome<Unit, RoomError> {
        val room = roomRepository.getRoom(roomId)
            ?: return Outcome.Error(RoomError.RoomNotFound(roomId))

        // 1. 驗證業務規則：檢查是否已經在房間內
        if (room.playerIds.contains(playerId)) {
            return Outcome.Error(RoomError.PlayerAlreadyInRoom(playerId, roomId))
        }

        // 2. 驗證業務規則：利用 Room 領域模型檢查是否已滿
        if (room.isFull) {
            return Outcome.Error(RoomError.RoomIsFull(roomId))
        }

        // 3. 更新領域模型並持久化
        val updatedRoom = room.copy(
            playerIds = room.playerIds + playerId
        )
        roomRepository.setRoom(updatedRoom)

        // 4. 同步給所有正在觀察的玩家
        val observers = snapshotRepository.getAllObservers(roomId)
        observers.forEach { observerId ->
            snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
        }

        // 5. 通知房間內的所有玩家
        updatedRoom.playerIds.forEach { memberId ->
            notificationService.notifyJoin(
                roomId = roomId,
                targetPlayerId = memberId,
                joinedPlayerId = playerId,
                reason = JoinReason.Joined
            )
        }

        return Outcome.Success(Unit)
    }
}