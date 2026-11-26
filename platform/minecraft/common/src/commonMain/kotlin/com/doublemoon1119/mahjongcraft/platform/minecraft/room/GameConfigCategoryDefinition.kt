package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 單一設定分類。
 *
 * @property id 分類的 Namespaced ID。
 * @property nameTranslationKey 分類名稱翻譯鍵。
 */
data class GameConfigCategoryDefinition(
    val id: String,
    val nameTranslationKey: String,
) {
    init {
        NamespacedId.requireValid(id) { "Config category ID must be namespaced: $id" }
        require(nameTranslationKey.isNotBlank()) { "Config category translation key must not be blank" }
    }
}
