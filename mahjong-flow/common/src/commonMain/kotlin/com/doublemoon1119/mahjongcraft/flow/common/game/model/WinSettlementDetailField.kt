package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 不使用字串 Map 的單一規則擴充欄位。 */
data class WinSettlementDetailField(val id: String, val value: WinSettlementDetailValue) {
    init {
        NamespacedId.requireValid(id) { "Win settlement detail field ID must be namespaced: $id" }
    }
}
