package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 規則自訂說明分類。
 *
 * @property id namespaced 分類識別碼。
 * @property nameTranslationKey 分類名稱翻譯鍵。
 */
data class RuleCatalogueCategory(
    val id: String,
    val nameTranslationKey: String,
) {
    init {
        NamespacedId.requireValid(id) { "Invalid catalogue category ID: $id" }
        require(nameTranslationKey.isNotBlank()) { "Catalogue category name key must not be blank" }
    }
}
