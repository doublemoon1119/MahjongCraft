package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState

/** 規則對動作後補牌的強型別決策。 */
sealed interface SupplementalDrawDecision {
    /** 此動作不需要補牌，候選桌況可直接成立。 */
    data object NotRequired : SupplementalDrawDecision

    /**
     * 補牌已成功解析。
     *
     * @property drawnTiles 依摸取順序排列的補牌；最後一張會放入玩家摸牌位置。
     * @property tileWall 更新後仍可供一般摸牌的牌堆。
     * @property reservedWallTiles 更新後的權威規則保留牌；實體擺法由另外的 layout policy 決定。
     * @property dynamicRuleState 更新後的規則動態狀態。
     */
    data class Completed(
        val drawnTiles: List<IdentifiedTile>,
        val tileWall: TileWall,
        val reservedWallTiles: List<IdentifiedTile>,
        val dynamicRuleState: DynamicRuleState?,
    ) : SupplementalDrawDecision

    /**
     * 規則拒絕完成補牌。
     *
     * @property reasonId 完整 namespaced 拒絕原因 ID。
     */
    data class Rejected(val reasonId: String) : SupplementalDrawDecision {
        init {
            NamespacedId.requireValid(reasonId) { "Supplemental draw rejection must use a namespaced id: $reasonId" }
        }
    }
}
