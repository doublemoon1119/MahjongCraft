package com.doublemoon1119.mahjongcraft.domain.room

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import java.util.*

/**
 * 遊戲房間領域模型。
 *
 * 負責管理遊戲開始前的玩家集合、準備狀態以及規則配置。
 * 在此階段僅記錄玩家的存在，具體的座位分配將在遊戲正式開始時決定。
 *
 * @property id 房間的唯一識別碼。
 * @property hostId 房主的玩家 ID，擁有更改配置與開始遊戲的權限。
 * @property config 該房間採用的麻將規則配置，必須實作 [MahjongRuleConfig]。
 * @property playerIds 目前在房間內的所有玩家 ID 集合。
 * @property readyPlayerIds 已點擊準備的玩家 ID 集合。
 */
data class Room(
    val id: UUID,
    val hostId: UUID,
    val config: MahjongRuleConfig,
    val playerIds: Set<UUID> = emptySet(),
    val readyPlayerIds: Set<UUID> = emptySet()
) {
    /**
     * 獲取此規則允許的人數範圍。
     */
    private val allowedRange: IntRange get() = config.minPlayers..config.maxPlayers

    /**
     * 檢查房間是否已達到人數上限。
     */
    val isFull: Boolean get() = playerIds.size >= config.maxPlayers

    /**
     * 檢查是否符合開始遊戲的條件。
     *
     * 條件：
     * 1. 人數在規則允許的範圍內。
     * 2. 所有在場玩家皆已準備。
     * 3. 確保準備名單中的所有玩家確實都存在於房間內。
     */
    val canStart: Boolean
        get() = playerIds.size in allowedRange &&
                playerIds.size == readyPlayerIds.size &&
                readyPlayerIds.containsAll(playerIds)
}