package com.doublemoon1119.mahjongcraft.flow.api.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 規則提供、可公開給事件訂閱者的狀態標記。
 *
 * @property id 標記的完整 namespaced ID。
 * @property playerId 標記所屬玩家；不屬於單一玩家時為 `null`。
 * @property quantity 標記附帶的可選數值。
 */
class PublicEventMarker private constructor(
    val id: String,
    val playerId: Uuid?,
    val quantity: EventQuantity?,
) {
    init {
        NamespacedId.requireValid(id) { "Public event marker ID must be namespaced: $id" }
    }

    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立公開標記。
         *
         * @param id 標記的完整 namespaced ID。
         * @param playerId 標記所屬玩家；不屬於單一玩家時為 `null`。
         * @param quantity 標記附帶的可選數值。
         * @return 建立的公開標記。
         */
        @JvmSynthetic
        fun create(id: String, playerId: Uuid?, quantity: EventQuantity?): PublicEventMarker = PublicEventMarker(id, playerId, quantity)
    }
}
