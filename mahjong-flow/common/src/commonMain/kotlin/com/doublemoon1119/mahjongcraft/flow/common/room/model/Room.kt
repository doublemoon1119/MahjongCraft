package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.uuid.Uuid

/**
 * 房間的權威領域模型。
 *
 * 代表伺服器端某個房間當下的完整狀態，包含成員、準備狀態與規則配置，
 * 為房間相關 use case 讀取與更新的唯一真實來源（Source of Truth）。
 *
 * @property id 房間的唯一識別碼。
 * @property hostId 房主的玩家 Uuid。
 * @property config 該房間採用的麻將規則配置。
 * @property playerIds 目前房間內所有玩家（含房主與 AI）的 Uuid 集合。
 * @property readyPlayerIds 已標記為「準備完成」的玩家 Uuid 集合（房主不計入此集合）。
 * @property aiPlayerIds 由房主新增的 AI 玩家 Uuid 集合。
 */
data class Room(
    val id: Uuid,
    val hostId: Uuid,
    val config: MahjongRuleConfig,
    val playerIds: Set<Uuid> = emptySet(),
    val readyPlayerIds: Set<Uuid> = emptySet(),
    val aiPlayerIds: Set<Uuid> = emptySet()
) {
    /** 依規則配置換算出的合法玩家人數區間。 */
    private val allowedRange: IntRange get() = config.minPlayers..config.maxPlayers

    /** 房間人數是否已達規則配置的上限。 */
    val isFull: Boolean get() = playerIds.size >= config.maxPlayers

    /**
     * 房間是否符合開局條件。
     *
     * 需同時滿足：目前人數落在規則允許的區間內，且除房主外的所有玩家皆已準備完成。
     */
    val canStart: Boolean
        get() {
            if (playerIds.size !in allowedRange) return false

            val otherPlayers = playerIds - hostId

            return readyPlayerIds.size == otherPlayers.size &&
                    readyPlayerIds.containsAll(otherPlayers)
        }

    /** 排除 AI 後，房間內實際的人類玩家 Uuid 集合。 */
    val humanPlayerIds: Set<Uuid> get() = playerIds - aiPlayerIds

    /**
     * 判斷指定玩家是否為 AI。
     *
     * @param playerId 欲檢查的玩家 Uuid。
     * @return 若該 Uuid 屬於 [aiPlayerIds] 則回傳 true。
     */
    fun isAi(playerId: Uuid): Boolean = aiPlayerIds.contains(playerId)
}
