package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import kotlin.uuid.Uuid

/** 規則對牌牆公開狀態的強型別決策。 */
sealed interface WallRevealDecision {
    /** 此節點不需要改變公開牌狀態。 */
    data object NoChange : WallRevealDecision

    /**
     * 公開牌狀態已成功更新。
     *
     * @property dynamicRuleState 更新後的規則動態狀態。
     * @property newlyRevealedTileIds 本次新成為公開資訊的牌張 ID。
     */
    data class Updated(
        val dynamicRuleState: DynamicRuleState,
        val newlyRevealedTileIds: Set<Uuid> = emptySet(),
    ) : WallRevealDecision

    /**
     * 規則拒絕更新公開牌狀態。
     *
     * @property reasonId 完整 namespaced 拒絕原因 ID。
     */
    data class Rejected(val reasonId: String) : WallRevealDecision {
        init {
            NamespacedId.requireValid(reasonId) { "Wall reveal rejection must use a namespaced id: $reasonId" }
        }
    }
}
