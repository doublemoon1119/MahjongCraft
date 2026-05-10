package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.application.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import java.util.UUID

/**
 * 玩家離開房間的應用層用例。
 *
 * 負責處理玩家主動退出或斷線時的房間狀態更新。若房主離開，則執行房間解散邏輯。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 */
class LeaveRoomUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository
) {
    /**
     * 執行離開房間邏輯。
     *
     * @param roomId 房間 UUID。
     * @param playerId 欲離開的玩家 UUID。
     */
    suspend operator fun invoke(
        roomId: UUID,
        playerId: UUID
    ) {
        val room = roomRepository.getRoom(roomId) ?: return

        // 1. 若離開者為房主，則解散房間
        if (playerId == room.hostId) {
            roomRepository.removeRoom(roomId)
            // 可在此處額外實作通知所有玩家房間已關閉的邏輯
            return
        }

        // 2. 若為普通玩家，更新成員清單與準備狀態
        val updatedRoom = room.copy(
            playerIds = room.playerIds - playerId,
            readyPlayerIds = room.readyPlayerIds - playerId
        )

        // 3. 儲存更新後的狀態
        roomRepository.setRoom(updatedRoom)

        // 4. 同步最新快照給留下的所有成員
        updatedRoom.playerIds.forEach { memberId ->
            val snapshot = updatedRoom.toSnapshot(memberId)
            snapshotRepository.setSnapshot(memberId, snapshot)
        }
    }
}