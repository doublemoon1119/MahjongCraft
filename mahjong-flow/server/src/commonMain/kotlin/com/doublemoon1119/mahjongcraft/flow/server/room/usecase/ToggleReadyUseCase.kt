package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 切換玩家準備狀態的應用層用例。
 *
 * 負責處理房間內非房主玩家的準備狀態變更。變更後會向所有相關觀察者同步最新的房間快照。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 */
@Factory
class ToggleReadyUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    @Provided private val eventPublisher: RoomEventPublisher,
) {
    /**
     * 執行準備狀態切換邏輯。
     *
     * @param roomId 房間 Uuid。
     * @param playerId 發起請求的玩家 Uuid。
     * @return 切換準備狀態的結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        playerId: Uuid,
    ): Outcome<Unit, RoomError> {
        // 1. 以原子方式讀取房間、切換準備狀態並寫回，避免並發切換請求互相覆蓋。
        //    房主不參與準備狀態切換，此情境下回傳 Success(null) 代表無需任何後續處理。
        val outcome = roomRepository.update(roomId) { room ->
            when {
                room == null -> room to Outcome.Error(RoomError.RoomNotFound(roomId))
                !room.playerIds.contains(playerId) -> room to Outcome.Error(RoomError.PlayerNotInRoom(playerId, roomId))
                playerId == room.hostId -> room to Outcome.Success(null)
                else -> {
                    val newReadyPlayerIds = if (room.readyPlayerIds.contains(playerId)) {
                        room.readyPlayerIds - playerId
                    } else {
                        room.readyPlayerIds + playerId
                    }
                    val updatedRoom = room.copy(readyPlayerIds = newReadyPlayerIds)
                    updatedRoom to Outcome.Success(updatedRoom)
                }
            }
        }

        return when (outcome) {
            is Outcome.Error -> outcome
            is Outcome.Success -> {
                val updatedRoom = outcome.value ?: return Outcome.Success(Unit)
                val isNowReady = updatedRoom.readyPlayerIds.contains(playerId)

                // 2. 向所有觀察者同步更新後的狀態
                val observers = snapshotRepository.getAllObservers(roomId)
                observers.forEach { observerId ->
                    snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
                }

                // 3. 通知房間內的所有成員
                updatedRoom.playerIds.forEach { memberId ->
                    eventPublisher.publishReady(
                        roomId = roomId,
                        targetPlayerId = memberId,
                        readyPlayerId = playerId,
                        isReady = isNowReady,
                    )
                }

                Outcome.Success(Unit)
            }
        }
    }
}
