package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.application.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.domain.room.RoomSnapshot
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import java.util.UUID

/**
 * 同步房間快照的應用層用例。
 *
 * 伺服器端在接收到客戶端的同步請求（如玩家進入區域或主動刷新）時，
 * 透過此用例從權威數據倉庫獲取房間狀態，並針對請求者的身分生成對應的快照進行同步。
 *
 * @property roomRepository 權威房間數據倉庫，用於獲取原始房間實體。
 * @property snapshotRepository 房間快照數據倉庫，用於觸發針對特定觀察者的同步行為。
 */
class SyncRoomSnapshotUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository
) {
    /**
     * 執行房間狀態的同步處理。
     *
     * 根據房間識別碼尋找權威實體，若存在則產出針對 [observerId] 的視角快照並存入快照倉庫。
     *
     * @param roomId 欲同步的房間 UUID。
     * @param observerId 發起請求或需要更新的觀察者玩家 UUID。
     */
    suspend operator fun invoke(
        roomId: UUID,
        observerId: UUID
    ) {
        val room = roomRepository.getRoom(roomId) ?: return

        // 產出針對特定觀察者過濾後的快照數據
        val snapshot = room.toSnapshot(observerId)

        // 透過倉庫層級觸發數據同步（在伺服器端實作中通常涉及發送網路封包）
        snapshotRepository.setSnapshot(observerId, snapshot)
    }
}