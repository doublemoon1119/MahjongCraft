package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 有單位的數值。
 *
 * @property unitId 規則定義的 namespaced 單位 ID，例如日麻的翻、符或役滿倍數。
 * @property amount 數值。
 */
data class WinSettlementQuantity(val unitId: String, val amount: Int) {
    init {
        NamespacedId.requireValid(unitId) { "Win settlement quantity unit ID must be namespaced: $unitId" }
    }
}
