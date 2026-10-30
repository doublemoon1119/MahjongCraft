package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet

/**
 * 已允許展示的牌面與公開特徵，不代表對局中的特定牌張。
 *
 * @property tile 基本牌面。
 * @property views 經明確投影的特徵，不含原始權威組件或授權名單。
 * @property appearanceId 已允許使用的外觀識別碼；沒有時採用一般外觀。
 */
data class TileFaceView(
    val tile: Tile,
    val views: ComponentViewSet = ComponentViewSet.EMPTY,
    val appearanceId: String? = null,
) {
    init {
        appearanceId?.let { NamespacedId.requireValid(it) { "Tile appearance ID must be namespaced: $it" } }
    }
}
