package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 將「外觀樣式＋基本牌面」綁定到顯示名稱的翻譯 key，例如赤五樣式的五萬顯示為「赤五萬」。
 *
 * @property styleId 牌張外觀樣式的完整 ID。
 * @property tile 套用樣式的基本牌面。
 * @property translationKey 對應顯示文字的翻譯 key。
 */
data class TileStyleDisplayNameDefinition(
    val styleId: String,
    val tile: Tile,
    val translationKey: String,
) {
    init {
        NamespacedId.requireValid(styleId) { "Tile style ID must be namespaced: $styleId" }
    }
}
