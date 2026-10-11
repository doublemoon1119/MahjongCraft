package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 一張等待牌的可用性。
 *
 * [winAvailability] 是規則模組自訂的命名字串，比照 [DiscardReadinessAnalysis.statusIndicatorId] 的
 * 慣例：[WaitingTileAvailabilityIds.WIN_AVAILABLE] 代表沒有任何和牌資格上的特殊限制，這是所有規則模組共通的中立
 * 預設；有更細分和牌可用性概念的規則模組（例如日麻的自摸限定、無役、未達最低翻符）另外提供各自的
 * 命名字串。
 *
 * @property tile 等待的牌種。
 * @property remainingCount 依可見資訊估算的剩餘張數。
 * @property winAvailability 這張牌的和牌資格識別碼。
 */
data class WaitingTileAvailability(
    val tile: Tile,
    val remainingCount: Int,
    val winAvailability: String,
)
