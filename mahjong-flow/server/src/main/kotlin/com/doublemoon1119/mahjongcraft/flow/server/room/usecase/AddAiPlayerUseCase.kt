package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomNotificationService
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.logic.room.toSnapshot
import java.util.*

/**
 * 在房間中新增電腦玩家（AI）的應用層用例。
 *
 * 負責處理房主發起的「新增 AI」請求。此用例會產生一個全新的 UUID 作為 AI 識別碼，
 * 並將其加入房間的成員清單與 AI 清單中，同時預設 AI 為已準備狀態。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property notificationService 房間通知服務。
 */
class AddAiPlayerUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    private val notificationService: RoomNotificationService
) {
    /**
     * 執行新增 AI 玩家邏輯。
     *
     * @param roomId 房間 UUID。
     * @param operatorId 發起請求的玩家 UUID（必須為房主）。
     * @return 新增 AI 的結果，成功時包含新產生的 AI 玩家 UUID，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: UUID,
        operatorId: UUID
    ): Outcome<UUID, RoomError> {
        val room = roomRepository.getRoom(roomId)
            ?: return Outcome.Error(RoomError.RoomNotFound(roomId))

        // 1. 權限驗證：僅限房主操作
        if (operatorId != room.hostId) {
            return Outcome.Error(RoomError.NotHost(operatorId))
        }

        // 2. 業務規則驗證：檢查人數上限
        if (room.isFull) {
            return Outcome.Error(RoomError.RoomIsFull(roomId))
        }

        // 3. 產生 AI 的 UUID 並更新領域模型，並持久化
        val aiId = UUID.randomUUID()
        val updatedRoom = room.copy(
            playerIds = room.playerIds + aiId,
            aiPlayerIds = room.aiPlayerIds + aiId,
            readyPlayerIds = room.readyPlayerIds + aiId  // AI 會直接進入準備就緒狀態
        )
        roomRepository.setRoom(updatedRoom)

        // 4. 同步給所有正在觀察的玩家
        val observers = snapshotRepository.getAllObservers(roomId)
        observers.forEach { observerId ->
            snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
        }

        // 5. 通知房間內所有成員
        updatedRoom.playerIds.forEach { memberId ->
            notificationService.notifyJoin(
                roomId = roomId,
                targetPlayerId = memberId,
                joinedPlayerId = aiId,
                reason = JoinReason.Joined
            )
        }

        return Outcome.Success(aiId)
    }
}