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
 * 處理房主剔除成員流程的應用層用例。
 *
 * 驗證房主權限後移除目標玩家，並明確標記移除原因為被剔除。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 */
@Factory
class KickPlayerUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    @Provided private val eventPublisher: RoomEventPublisher
) {
    /**
     * 執行剔除玩家邏輯。
     *
     * @param roomId 房間 Uuid。
     * @param operatorId 發起剔除請求的玩家 Uuid。
     * @param targetPlayerId 被剔除的目標玩家 Uuid。
     * @return 剔除結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        operatorId: Uuid,
        targetPlayerId: Uuid
    ): Outcome<Unit, RoomError> {
        // 1. 以原子方式讀取房間、驗證業務規則並寫回，避免與其他房間操作（如玩家自行離開）產生競態
        val outcome = roomRepository.update(roomId) { room ->
            when {
                room == null -> room to Outcome.Error(RoomError.RoomNotFound(roomId))
                operatorId != room.hostId -> room to Outcome.Error(RoomError.NotHost(operatorId))
                !room.playerIds.contains(targetPlayerId) -> room to Outcome.Error(RoomError.PlayerNotInRoom(targetPlayerId, roomId))
                targetPlayerId == room.hostId -> room to Outcome.Error(RoomError.HostCannotKickSelf(targetPlayerId))
                else -> {
                    val updatedRoom = room.copy(
                        playerIds = room.playerIds - targetPlayerId,
                        readyPlayerIds = room.readyPlayerIds - targetPlayerId
                    )
                    updatedRoom to Outcome.Success(updatedRoom)
                }
            }
        }

        return when (outcome) {
            is Outcome.Error -> outcome
            is Outcome.Success -> {
                val updatedRoom = outcome.value

                // 2. 同步新狀態給所有觀察者
                val observers = snapshotRepository.getAllObservers(roomId)
                observers.forEach { observerId ->
                    snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
                }

                // 3. 通知**原房間**內的所有玩家（含被剔除者本人，故需補回 targetPlayerId）
                (updatedRoom.playerIds + targetPlayerId).forEach { memberId ->
                    eventPublisher.publishLeave(
                        roomId = roomId,
                        targetPlayerId = memberId,
                        leftPlayerId = targetPlayerId,
                        reason = LeaveReason.Kicked
                    )
                }

                Outcome.Success(Unit)
            }
        }
    }
}