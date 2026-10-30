package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 規則在公開事件中提供的一個規則中立標記。
 *
 * @property id 完整的公開標記識別碼。
 * @property playerId 標記所指向的玩家；整場標記為 null。
 * @property quantity 標記附帶的規則中立數值；沒有數值時為 null。
 */
data class RulePublicEventMarker(
    val id: String,
    val playerId: Uuid? = null,
    val quantity: RulePublicEventQuantity? = null,
) {
    init {
        NamespacedId.requireValid(id) { "Public event marker ID must be namespaced: $id" }
    }
}
