package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.application.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import java.util.UUID

/**
 * 玩家加入房間的應用層用例。
 *
 * 負責處理玩家請求進入特定房間的邏輯，包含房間狀態檢查、人數限制驗證以及全體成員的狀態同步。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 */
class JoinRoomUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository
) {
    /**
     * 執行加入房間邏輯。
     *
     * @param roomId 欲加入的房間 UUID。
     * @param playerId 請求加入的玩家 UUID。
     * @throws IllegalStateException 若房間不存在、房間已滿、或玩家已在房間內。
     */
    suspend operator fun invoke(
        roomId: UUID,
        playerId: UUID
    ) {
        val room = roomRepository.getRoom(roomId)
            ?: throw IllegalStateException("Room with id $roomId does not exist.")

        // 1. 驗證業務規則：檢查是否已經在房間內
        if (room.playerIds.contains(playerId)) {
            throw IllegalStateException("Player $playerId is already in the room.")
        }

        // 2. 驗證業務規則：利用 Room 領域模型檢查是否已滿
        if (room.isFull) {
            throw IllegalStateException("Room $roomId is already full.")
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
    }
}