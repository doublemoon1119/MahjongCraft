package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import kotlin.uuid.Uuid

/** 規則對牌牆公開狀態的強型別決策。 */
sealed interface WallRevealDecision {
    /** 此節點不需要改變公開牌狀態。 */
    data object NoChange : WallRevealDecision

    /**
     * 公開牌狀態已成功更新。
     *
     * @property tableComponents 本次明確設定或移除的桌況組件；未指定的組件保持原值。
     * @property newlyRevealedTileIds 本次新成為公開資訊的牌張 ID。
     */
    data class Updated(
        val tableComponents: ComponentPatch,
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
