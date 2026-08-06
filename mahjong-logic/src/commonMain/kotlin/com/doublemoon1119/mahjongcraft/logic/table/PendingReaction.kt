package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 捨牌後、等待其他玩家回應（吃/碰/槓/過）的反應視窗狀態。
 *
 * 規則無關的通用型別：哪些玩家有資格回應、回應的優先權判斷等規則特有邏輯，
 * 一律交由呼叫端（如 use case）搭配 [com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator] 決定，
 * 這裡只負責追蹤「誰還沒回應」與「大家是否都回應完了」。
 *
 * @property discarderId 本次捨牌的玩家 Uuid。
 * @property tileId 本次被捨棄、等待被回應的牌的唯一識別碼。
 * @property eligiblePlayerIds 有資格對本次捨牌做出回應的玩家 Uuid 集合。
 * @property responses 目前已收到的回應，鍵為玩家 Uuid。
 */
data class PendingReaction(
    val discarderId: Uuid,
    val tileId: Uuid,
    val eligiblePlayerIds: Set<Uuid>,
    val responses: Map<Uuid, GameAction> = emptyMap()
) {
    /** 是否所有有資格的玩家都已經回應。 */
    val isComplete: Boolean get() = responses.keys.containsAll(eligiblePlayerIds)
}
