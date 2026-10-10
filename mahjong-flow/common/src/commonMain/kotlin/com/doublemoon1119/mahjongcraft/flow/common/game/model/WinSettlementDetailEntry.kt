package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 胡牌詳情的一個條目。
 *
 * @property id 規則定義的 namespaced 條目 ID，例如日麻的役種。
 * @property quantity 條目附帶的數值；沒有數值的條目為 null。
 */
data class WinSettlementDetailEntry(val id: String, val quantity: WinSettlementQuantity? = null) {
    init {
        NamespacedId.requireValid(id) { "Win settlement detail entry ID must be namespaced: $id" }
    }
}
