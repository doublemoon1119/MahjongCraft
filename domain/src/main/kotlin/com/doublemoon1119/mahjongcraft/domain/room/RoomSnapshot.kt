package com.doublemoon1119.mahjongcraft.domain.room

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import java.util.UUID

/**
 * 房間狀態快照。
 *
 * 為唯讀的數據載體，用於在客戶端渲染 GUI 或供旁觀者查看。
 *
 * @property id 房間的唯一識別碼。
 * @property hostId 房主的玩家 ID。
 * @property config 房間規則配置。
 * @property playerIds 目前在房間內的玩家集合。
 * @property readyPlayerIds 已準備的玩家集合。
 * @property canStart 是否已達開賽門檻。
 * @property isHost 接收此快照的玩家是否為房主。
 * @property isInRoom 接收此快照的玩家是否已加入房間。
 */
data class RoomSnapshot(
    val id: UUID,
    val hostId: UUID,
    val config: MahjongRuleConfig,
    val playerIds: Set<UUID>,
    val readyPlayerIds: Set<UUID>,
    val canStart: Boolean,
    val isHost: Boolean,
    val isInRoom: Boolean
)

/**
 * 將 [Room] 領域模型轉換為針對特定觀察者的快照。
 *
 * @param observerId 觀察者的玩家 ID。
 * @return 根據觀察者權限過濾後的 [RoomSnapshot]。
 */
fun Room.toSnapshot(observerId: UUID): RoomSnapshot {
    return RoomSnapshot(
        id = id,
        hostId = hostId,
        config = config,
        playerIds = playerIds,
        readyPlayerIds = readyPlayerIds,
        canStart = canStart,
        isHost = observerId == hostId,
        isInRoom = playerIds.contains(observerId)
    )
}