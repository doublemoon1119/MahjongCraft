package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.application.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import java.util.UUID

/**
 * 切換玩家準備狀態的應用層用例。
 *
 * 負責處理房間內非房主玩家的準備狀態變更。變更後會向所有相關觀察者同步最新的房間快照。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 */
class ToggleReadyUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository
) {
    /**
     * 執行準備狀態切換邏輯。
     *
     * @param roomId 房間 UUID。
     * @param playerId 發起請求的玩家 UUID。
     * @throws IllegalStateException 若房間不存在或玩家不在房間內。
     */
    suspend operator fun invoke(
        roomId: UUID,
        playerId: UUID
    ) {
        val room = roomRepository.getRoom(roomId)
            ?: throw IllegalStateException("Room with id $roomId does not exist.")

        // 1. 驗證玩家是否在房間內
        if (!room.playerIds.contains(playerId)) {
            throw IllegalStateException("Player $playerId is not in the room.")
        }

        // 2. 房主不參與準備狀態切換（業務邏輯限制）
        if (playerId == room.hostId) {
            return
        }

        // 3. 計算新的準備狀態集合
        val newReadyPlayerIds = if (room.readyPlayerIds.contains(playerId)) {
            room.readyPlayerIds - playerId
        } else {
            room.readyPlayerIds + playerId
        }

        // 4. 更新領域模型並持久化
        val updatedRoom = room.copy(readyPlayerIds = newReadyPlayerIds)
        roomRepository.setRoom(updatedRoom)

        // 5. 向所有觀察者同步更新後的狀態
        val observers = snapshotRepository.getAllObservers(roomId)
        observers.forEach { observerId ->
            snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
        }
    }
}