package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 玩家離開房間的應用層用例。
 *
 * 負責處理玩家主動退出或斷線時的房間狀態更新。若房主離開，則執行房間解散邏輯。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 */
@Factory
class LeaveRoomUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    @Provided private val eventPublisher: RoomEventPublisher
) {
    /**
     * 執行離開房間邏輯。
     *
     * @param roomId 房間 Uuid。
     * @param playerId 欲離開的玩家 Uuid。
     * @return 離開房間的結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        playerId: Uuid
    ): Outcome<Unit, RoomError> {
        val room = roomRepository.getRoom(roomId)
            ?: return Outcome.Error(RoomError.RoomNotFound(roomId))

        // 1. 若離開者為房主，則解散房間
        if (playerId == room.hostId) {
            roomRepository.removeRoom(roomId)
            room.playerIds.forEach { memberId ->
                // 通知房間內的所有玩家，並移除快照
                eventPublisher.publishLeave(
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
            eventPublisher.publishLeave(
                roomId = roomId,
                targetPlayerId = memberId,
                leftPlayerId = playerId,
                reason = LeaveReason.Voluntary
            )
        }

        return Outcome.Success(Unit)
    }
}