package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 胡牌詳情的一個條目。
 *
 * @property id 規則定義的 namespaced 條目 ID，例如日麻的役種。
 * @property quantity 條目附帶的數值；沒有數值的條目為 null。
 * @property nameQuantity 出現在條目名稱裡的數值，例如連莊次數；名稱不帶數值時為 null。
 * @property appliesToSomePayersOnly 條目只適用於部分付款者，例如只有其中一家多付的台數；適用於所有付款者時為 false。
 */
data class WinSettlementDetailEntry(
    val id: String,
    val quantity: WinSettlementQuantity? = null,
    val nameQuantity: WinSettlementQuantity? = null,
    val appliesToSomePayersOnly: Boolean = false,
) {
    init {
        NamespacedId.requireValid(id) { "Win settlement detail entry ID must be namespaced: $id" }
    }
}
