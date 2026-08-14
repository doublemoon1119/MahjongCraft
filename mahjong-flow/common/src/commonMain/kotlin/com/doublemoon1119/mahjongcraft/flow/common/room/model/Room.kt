package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import kotlin.uuid.Uuid

/**
 * 房間的權威領域模型。
 *
 * 代表伺服器端某個房間當下的完整狀態，包含成員、準備狀態與規則配置，
 * 為房間相關 use case 讀取與更新的唯一真實來源（Source of Truth）。
 *
 * @property id 房間的唯一識別碼。
 * @property hostId 房主的玩家 Uuid。
 * @property gameConfig 該房間開局時採用的完整遊戲設定。
 * @property playerIds 目前房間內所有玩家（含房主與 AI）的 Uuid，依加入房間的順序排列，不會重複。
 * @property readyPlayerIds 已標記為「準備完成」的玩家 Uuid（房主不計入此集合），不會重複。
 * @property aiPlayerStrategyKeys 由房主新增的 AI 玩家 Uuid 對應到其 AI 策略登記 key 的映射。
 */
data class Room(
    val id: Uuid,
    val hostId: Uuid,
    val gameConfig: GameConfig,
    val playerIds: List<Uuid> = emptyList(),
    val readyPlayerIds: List<Uuid> = emptyList(),
    val aiPlayerStrategyKeys: Map<Uuid, String> = emptyMap(),
) {
    /** 依規則配置換算出的合法玩家人數區間。 */
    private val allowedRange: IntRange get() = gameConfig.ruleConfig.minPlayers..gameConfig.ruleConfig.maxPlayers

    /** 由房主新增的 AI 玩家 Uuid，依 [playerIds] 的加入順序排列。 */
    val aiPlayerIds: List<Uuid> get() = playerIds.filter { it in aiPlayerStrategyKeys }

    /** 房間人數是否已達規則配置的上限。 */
    val isFull: Boolean get() = playerIds.size >= gameConfig.ruleConfig.maxPlayers

    /** 目前人數是否落在規則配置允許的區間內。 */
    val isPlayerCountValid: Boolean get() = playerIds.size in allowedRange

    /** 除房主外的所有玩家是否皆已準備完成。 */
    private val allOthersReady: Boolean
        get() {
            val otherPlayers = playerIds - hostId
            return readyPlayerIds.size == otherPlayers.size && readyPlayerIds.containsAll(otherPlayers)
        }

    /**
     * 房間是否符合開局條件。
     *
     * 需同時滿足：目前人數落在規則允許的區間內，且除房主外的所有玩家皆已準備完成。
     */
    val canStart: Boolean get() = isPlayerCountValid && allOthersReady

    /** 排除 AI 後，房間內實際的人類玩家 Uuid，依 [playerIds] 的加入順序排列。 */
    val humanPlayerIds: List<Uuid> get() = playerIds - aiPlayerIds

    /**
     * 判斷指定玩家是否為 AI。
     *
     * @param playerId 欲檢查的玩家 Uuid。
     * @return 若該 Uuid 屬於 [aiPlayerIds] 則回傳 true。
     */
    fun isAi(playerId: Uuid): Boolean = aiPlayerStrategyKeys.containsKey(playerId)
}
