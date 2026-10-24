package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 公開事件標記所帶的規則中立數值。
 *
 * @property unitId 完整的數值單位識別碼。
 * @property amount 數值；正負意義由提供標記的規則定義。
 */
data class RulePublicEventQuantity(
    val unitId: String,
    val amount: Int,
) {
    init {
        NamespacedId.requireValid(unitId) { "Public event marker unit ID must be namespaced: $unitId" }
    }
}
