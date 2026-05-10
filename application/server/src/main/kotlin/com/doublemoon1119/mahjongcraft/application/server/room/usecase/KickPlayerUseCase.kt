package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.application.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import java.util.UUID

/**
 * 處理房主剔除成員流程的應用層用例。
 *
 * 驗證房主權限後移除目標玩家，並明確標記移除原因為被剔除。
 */
class KickPlayerUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository
) {
    /**
     * 執行剔除玩家邏輯。
     *
     * @param roomId 房間 UUID。
     * @param operatorId 發起剔除請求的玩家 UUID。
     * @param targetPlayerId 被剔除的目標玩家 UUID。
     * @throws IllegalStateException 若房間不存在、發起者非房主、或目標不在房間內。
     */
    suspend operator fun invoke(
        roomId: UUID,
        operatorId: UUID,
        targetPlayerId: UUID
    ) {
        val room = roomRepository.getRoom(roomId)
            ?: throw IllegalStateException("Room with id $roomId does not exist.")

        // 1. 驗證發起者是否為房主
        if (operatorId != room.hostId) {
            throw IllegalStateException("Only the host can kick players.")
        }

        // 2. 驗證目標是否在房間內且不是房主本人
        if (!room.playerIds.contains(targetPlayerId)) {
            throw IllegalStateException("Player $targetPlayerId is not in the room.")
        }
        if (targetPlayerId == room.hostId) {
            throw IllegalStateException("Host cannot kick themselves.")
        }

        // 3. 更新房間資料並持久化
        val updatedRoom = room.copy(
            playerIds = room.playerIds - targetPlayerId,
            readyPlayerIds = room.readyPlayerIds - targetPlayerId
        )
        roomRepository.setRoom(updatedRoom)

        // 4. 通知被剔除的玩家，並指定原因為 Kicked
        snapshotRepository.removeSnapshot(roomId, targetPlayerId, LeaveReason.Kicked)

        // 5. 同步新狀態給留下的成員
        updatedRoom.playerIds.forEach { memberId ->
            snapshotRepository.setSnapshot(memberId, updatedRoom.toSnapshot(memberId))
        }
    }
}