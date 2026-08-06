package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
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
 * @property gameRepository 權威對局數據倉庫，用於檢查該識別碼是否已有進行中的對局。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 */
@Factory
class CreateRoomUseCase(
    private val roomRepository: RoomRepository,
    private val gameRepository: GameRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    @Provided private val eventPublisher: RoomEventPublisher,
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
        config: MahjongRuleConfig,
    ): Outcome<Room, RoomError> {
        // 1. 以原子方式檢查房間是否已存在並寫入，避免並發請求重複創建同一房間
        val outcome = roomRepository.update(roomId) { existing ->
            when {
                existing != null -> existing to Outcome.Error(RoomError.RoomAlreadyExists(roomId))
                // 同一識別碼已有進行中的對局時拒絕建立，避免蓋掉該 BlockEntity 現有的遊戲狀態
                gameRepository.getTableState(roomId) != null ->
                    existing to Outcome.Error(RoomError.GameAlreadyInProgress(roomId))
                else -> {
                    // 初始化房間物件，房主預設加入且不預設準備
                    val newRoom = Room(
                        id = roomId,
                        hostId = hostId,
                        config = config,
                        playerIds = setOf(hostId),
                        readyPlayerIds = emptySet(),
                    )
                    newRoom to Outcome.Success(newRoom)
                }
            }
        }

        if (outcome is Outcome.Success) {
            val newRoom = outcome.value

            // 2. 同步給所有正在觀察的玩家
            val observers = snapshotRepository.getAllObservers(roomId)
            observers.forEach { observerId ->
                snapshotRepository.setSnapshot(observerId, newRoom.toSnapshot(observerId))
            }

            // 3. 發送創建房間通知
            eventPublisher.publishJoin(roomId, hostId, hostId, JoinReason.Created)
        }

        return outcome
    }
}
