package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.application.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.Room
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import java.util.*

/**
 * 創建房間的實例化用例。
 *
 * 負責處理玩家發起的開房請求，初始化房間狀態並同步至相關觀察者。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 */
class CreateRoomUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository
) {
    /**
     * 執行創建房間邏輯。
     *
     * @param roomId 房間的唯一識別碼（通常對應 BlockEntity UUID）。
     * @param hostId 房主的玩家 UUID。
     * @param config 房間採用的規則配置。
     * @return 創建成功的 [Room] 實例。
     * @throws IllegalStateException 若該 roomId 已存在房間。
     */
    suspend operator fun invoke(
        roomId: UUID,
        hostId: UUID,
        config: MahjongRuleConfig
    ): Room {
        // 1. 檢查房間是否已存在，避免重複創建
        if (roomRepository.getRoom(roomId) != null) {
            throw IllegalStateException("Room with id $roomId already exists.")
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

        // 4. 產生針對房主的初始快照並同步
        val initialSnapshot = newRoom.toSnapshot(hostId)
        snapshotRepository.setSnapshot(hostId, initialSnapshot)

        return newRoom
    }
}