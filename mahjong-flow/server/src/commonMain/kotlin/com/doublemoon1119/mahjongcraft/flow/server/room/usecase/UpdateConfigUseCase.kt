package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 更新房間配置的應用層用例。
 *
 * 僅允許房主修改房間規則配置。變更後會同步快照給所有觀察者，並通知所有房間成員。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 */
@Factory
class UpdateConfigUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    @Provided private val eventPublisher: RoomEventPublisher
) {
    /**
     * 執行更新配置邏輯。
     *
     * @param roomId 房間 Uuid。
     * @param operatorId 發起請求的玩家 Uuid（必須為房主）。
     * @param newConfig 新的規則配置。
     * @return 更新配置的結果，成功時為 [Unit]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        operatorId: Uuid,
        newConfig: MahjongRuleConfig
    ): Outcome<Unit, RoomError> {
        // 1. 以原子方式讀取房間、驗證權限並寫回，避免與其他房間操作產生競態
        val outcome = roomRepository.update(roomId) { room ->
            when {
                room == null -> room to Outcome.Error(RoomError.RoomNotFound(roomId))
                operatorId != room.hostId -> room to Outcome.Error(RoomError.NotHost(operatorId))
                else -> {
                    val updatedRoom = room.copy(config = newConfig)
                    updatedRoom to Outcome.Success(updatedRoom)
                }
            }
        }

        return when (outcome) {
            is Outcome.Error -> outcome
            is Outcome.Success -> {
                val updatedRoom = outcome.value

                // 2. 同步最新快照給所有觀察者
                val observers = snapshotRepository.getAllObservers(roomId)
                observers.forEach { observerId ->
                    snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
                }

                // 3. 通知房間內的所有成員（用於顯示系統訊息或提示）
                updatedRoom.playerIds.forEach { memberId ->
                    eventPublisher.publishConfigChanged(
                        roomId = roomId,
                        targetPlayerId = memberId,
                        newConfig = newConfig
                    )
                }

                Outcome.Success(Unit)
            }
        }
    }
}