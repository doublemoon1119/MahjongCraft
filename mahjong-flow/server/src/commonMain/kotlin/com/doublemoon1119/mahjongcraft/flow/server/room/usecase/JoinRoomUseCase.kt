package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 玩家加入房間的應用層用例。
 *
 * 負責處理玩家請求進入特定房間的邏輯，包含房間狀態檢查、人數限制驗證以及全體成員的狀態同步。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 */
@Factory
class JoinRoomUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    @Provided private val eventPublisher: RoomEventPublisher
) {
    /**
     * 執行加入房間邏輯。
     *
     * @param roomId 欲加入的房間 Uuid。
     * @param playerId 請求加入的玩家 Uuid。
     * @return 加入結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        playerId: Uuid
    ): Outcome<Unit, RoomError> {
        // 1. 以原子方式讀取房間、驗證業務規則並寫回，避免並發加入請求互相覆蓋
        val outcome = roomRepository.update(roomId) { room ->
            when {
                room == null -> room to Outcome.Error(RoomError.RoomNotFound(roomId))
                room.playerIds.contains(playerId) -> room to Outcome.Error(RoomError.PlayerAlreadyInRoom(playerId, roomId))
                room.isFull -> room to Outcome.Error(RoomError.RoomIsFull(roomId))
                else -> {
                    val updatedRoom = room.copy(playerIds = room.playerIds + playerId)
                    updatedRoom to Outcome.Success(updatedRoom)
                }
            }
        }

        return when (outcome) {
            is Outcome.Error -> outcome
            is Outcome.Success -> {
                val updatedRoom = outcome.value

                // 2. 同步給所有正在觀察的玩家
                val observers = snapshotRepository.getAllObservers(roomId)
                observers.forEach { observerId ->
                    snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
                }

                // 3. 通知房間內的所有玩家
                updatedRoom.playerIds.forEach { memberId ->
                    eventPublisher.publishJoin(
                        roomId = roomId,
                        targetPlayerId = memberId,
                        joinedPlayerId = playerId,
                        reason = JoinReason.Joined
                    )
                }

                Outcome.Success(Unit)
            }
        }
    }
}