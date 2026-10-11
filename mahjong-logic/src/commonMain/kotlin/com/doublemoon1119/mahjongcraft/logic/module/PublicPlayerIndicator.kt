package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 單一規則公開狀態；不得承載振聽、手牌內容等私人資訊。
 *
 * @property id 公開狀態的完整命名空間識別碼。
 * @property indicatorValue 此公開狀態的標記、次數或選項。
 */
data class PublicPlayerIndicator(
    val id: String,
    val indicatorValue: PublicPlayerIndicatorValue = PublicPlayerIndicatorValue.Marker,
) {
    init {
        NamespacedId.requireValid(id) { "Public player indicator ID must be namespaced: $id" }
    }
}
