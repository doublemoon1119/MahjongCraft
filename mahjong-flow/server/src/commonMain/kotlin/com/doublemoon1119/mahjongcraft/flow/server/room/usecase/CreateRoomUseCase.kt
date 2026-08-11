package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 創建房間的實例化用例。
 *
 * 負責處理玩家發起的開房請求，初始化房間狀態並同步至相關觀察者。
 *
 * @property authoritativeStateStore Room 與 Game 共用的權威狀態儲存。
 * @property membershipRepository 玩家唯一麻將桌歸屬倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 */
@Factory
class CreateRoomUseCase(
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val membershipRepository: PlayerMembershipRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    @Provided private val eventPublisher: RoomEventPublisher,
) {
    /**
     * 使用完整規則與流程設定建立房間。
     *
     * @param roomId 房間的唯一識別碼。
     * @param hostId 房主的玩家 Uuid。
     * @param gameConfig 房間開局時採用的完整遊戲設定。
     * @return 創建結果，成功時包含 [Room] 實例，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        hostId: Uuid,
        gameConfig: GameConfig,
    ): Outcome<Room, RoomError> {
        val existingTableId = membershipRepository.getTableId(hostId)
        if (existingTableId != null && existingTableId != roomId) {
            return Outcome.Error(RoomError.PlayerAlreadyInAnotherGame(hostId, existingTableId))
        }
        if (!membershipRepository.claim(hostId, roomId)) {
            val occupiedTableId = checkNotNull(membershipRepository.getTableId(hostId))
            return Outcome.Error(RoomError.PlayerAlreadyInAnotherGame(hostId, occupiedTableId))
        }

        // 1. 在同一筆 store 交易中檢查 Room／Game 並建立 Room，避免巢狀 repository 鎖與競態條件。
        val outcome = authoritativeStateStore.update { state ->
            when {
                state.rooms[roomId] != null ->
                    AuthoritativeStateUpdate(state, Outcome.Error(RoomError.RoomAlreadyExists(roomId)))
                state.games[roomId] != null ->
                    AuthoritativeStateUpdate(state, Outcome.Error(RoomError.GameAlreadyInProgress(roomId)))
                else -> {
                    // 初始化房間物件，房主預設加入且不預設準備
                    val newRoom = Room(
                        id = roomId,
                        hostId = hostId,
                        gameConfig = gameConfig,
                        playerIds = setOf(hostId),
                        readyPlayerIds = emptySet(),
                    )
                    AuthoritativeStateUpdate(
                        state.copy(rooms = state.rooms + (roomId to newRoom)),
                        Outcome.Success(newRoom),
                    )
                }
            }
        }

        if (outcome is Outcome.Error && existingTableId == null) {
            membershipRepository.release(hostId, roomId)
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
