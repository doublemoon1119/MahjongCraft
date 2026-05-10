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
 * @property playerIds 目前在房間內的所有玩家 ID 集合（包含房主）。
 * @property readyPlayerIds 已點擊準備的玩家 ID 集合（不包含房主）。
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
     * 驗證邏輯：
     * 1. 總人數位於 [MahjongRuleConfig] 定義的合法範圍內。
     * 2. 除了房主以外的所有玩家皆已在準備名單中。
     * 3. 確保準備名單中沒有不在房間內的外部玩家。
     *
     * @return 若符合開局條件則返回 true。
     */
    val canStart: Boolean
        get() {
            // 1. 人數要在範圍內
            if (playerIds.size !in allowedRange) return false

            // 2. 獲取除了房主以外的所有玩家
            val otherPlayers = playerIds - hostId

            // 3. 檢查「其他玩家」是否與「準備玩家」完全一致
            return readyPlayerIds.size == otherPlayers.size &&
                    readyPlayerIds.containsAll(otherPlayers)
        }
}