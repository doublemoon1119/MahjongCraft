package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.result.Outcome
import com.doublemoon1119.mahjongcraft.application.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.application.common.room.service.RoomNotificationService
import com.doublemoon1119.mahjongcraft.application.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import java.util.*

/**
 * 更新房間配置的應用層用例。
 *
 * 僅允許房主修改房間規則配置。變更後會同步快照給所有觀察者，並通知所有房間成員。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property notificationService 房間通知服務。
 */
class UpdateConfigUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    private val notificationService: RoomNotificationService
) {
    /**
     * 執行更新配置邏輯。
     *
     * @param roomId 房間 UUID。
     * @param operatorId 發起請求的玩家 UUID（必須為房主）。
     * @param newConfig 新的規則配置。
     * @return 更新配置的結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: UUID,
        operatorId: UUID,
        newConfig: MahjongRuleConfig
    ): Outcome<Unit, RoomError> {
        val room = roomRepository.getRoom(roomId)
            ?: return Outcome.Error(RoomError.RoomNotFound(roomId))

        // 1. 權限驗證：只有房主能修改配置
        if (operatorId != room.hostId) {
            return Outcome.Error(RoomError.NotHost(operatorId))
        }

        // 2. 更新領域模型並持久化
        val updatedRoom = room.copy(config = newConfig)
        roomRepository.setRoom(updatedRoom)

        // 3. 同步最新快照給所有觀察者
        val observers = snapshotRepository.getAllObservers(roomId)
        observers.forEach { observerId ->
            snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
        }

        // 4. 通知房間內的所有成員（用於顯示系統訊息或提示）
        updatedRoom.playerIds.forEach { memberId ->
            notificationService.notifyConfigChanged(
                roomId = roomId,
                targetPlayerId = memberId,
                newConfig = newConfig
            )
        }

        return Outcome.Success(Unit)
    }
}