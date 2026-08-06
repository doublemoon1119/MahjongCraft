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
    @Provided private val eventPublisher: RoomEventPublisher,
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
        playerId: Uuid,
    ): Outcome<Unit, RoomError> {
        // 1. 以原子方式讀取房間並決定「解散」或「移除單一玩家」，避免與其他房間操作產生競態。
        //    解散情境下持久層寫回 null（移除房間），並將解散前的房間狀態帶出供後續通知使用。
        val outcome = roomRepository.update(roomId) { room ->
            when {
                room == null -> room to Outcome.Error(RoomError.RoomNotFound(roomId))
                playerId == room.hostId -> null to Outcome.Success(room)
                else -> {
                    val updatedRoom = room.copy(
                        playerIds = room.playerIds - playerId,
                        readyPlayerIds = room.readyPlayerIds - playerId,
                    )
                    updatedRoom to Outcome.Success(updatedRoom)
                }
            }
        }

        return when (outcome) {
            is Outcome.Error -> outcome
            is Outcome.Success -> {
                val resultRoom = outcome.value

                if (playerId == resultRoom.hostId) {
                    // 2a. 房主離開：房間已被移除，通知解散前的所有成員並清除其快照
                    resultRoom.playerIds.forEach { memberId ->
                        eventPublisher.publishLeave(
                            roomId = roomId,
                            targetPlayerId = memberId,
                            leftPlayerId = memberId,
                            reason = LeaveReason.Dissolved,
                        )
                        snapshotRepository.removeSnapshot(roomId, memberId)
                    }
                } else {
                    // 2b. 一般玩家離開：同步最新快照給所有觀察者
                    val observers = snapshotRepository.getAllObservers(roomId)
                    observers.forEach { observerId ->
                        snapshotRepository.setSnapshot(observerId, resultRoom.toSnapshot(observerId))
                    }

                    // 通知**原房間**內的所有玩家（含離開者本人，故需補回 playerId）
                    (resultRoom.playerIds + playerId).forEach { memberId ->
                        eventPublisher.publishLeave(
                            roomId = roomId,
                            targetPlayerId = memberId,
                            leftPlayerId = playerId,
                            reason = LeaveReason.Voluntary,
                        )
                    }
                }

                Outcome.Success(Unit)
            }
        }
    }
}
