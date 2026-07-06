package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomNotificationService
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 創建房間的實例化用例。
 *
 * 負責處理玩家發起的開房請求，初始化房間狀態並同步至相關觀察者。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property notificationService 房間通知服務。
 */
@Factory
class CreateRoomUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    @Provided private val notificationService: RoomNotificationService
) {
    /**
     * 執行創建房間邏輯。
     *
     * @param roomId 房間的唯一識別碼（通常對應 BlockEntity Uuid）。
     * @param hostId 房主的玩家 Uuid。
     * @param config 房間採用的規則配置。
     * @return 創建結果，成功時包含 [Room] 實例，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        hostId: Uuid,
        config: MahjongRuleConfig
    ): Outcome<Room, RoomError> {
        // 1. 檢查房間是否已存在，避免重複創建
        if (roomRepository.getRoom(roomId) != null) {
            return Outcome.Error(RoomError.RoomAlreadyExists(roomId))
        }

        // 2. 初始化房間物件，房主預設加入且不預設準備
        val newRoom = Room(
            id = roomId,
            hostId = hostId,
            config = config,
            playerIds = setOf(hostId),
            readyPlayerIds = emptySet()
        )

        // 3. 存入權威數據倉庫
        roomRepository.setRoom(newRoom)

        // 4. 同步給所有正在觀察的玩家
        val observers = snapshotRepository.getAllObservers(roomId)
        observers.forEach { observerId ->
            snapshotRepository.setSnapshot(observerId, newRoom.toSnapshot(observerId))
        }

        // 5. 發送創建房間通知
        notificationService.notifyJoin(roomId, hostId, hostId, JoinReason.Created)

        return Outcome.Success(newRoom)
    }
}