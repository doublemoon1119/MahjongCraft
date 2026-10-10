package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 單一規則說明；價值與限制以翻譯標籤表示，不假設固定計分單位。
 *
 * @property id namespaced 條目識別碼。
 * @property categoryId 主要分類識別碼。
 * @property nameTranslationKey 名稱翻譯鍵。
 * @property descriptionTranslationKey 完整說明翻譯鍵。
 * @property labels 價值、限制或情境標籤。
 * @property unavailableReasonTranslationKey 依實際設定不適用時的原因；null 表示適用，或是沒有實際設定的一般說明。
 * @property examples 可選的完整手牌或局部示意，不要求存在牌面資產。
 * @property additionalCategoryIds 條目也列在其中的其他分類，例如日麻古役同時屬於翻數分類與古役分類；不含主要分類，不重複。
 */
data class RuleCatalogueEntry(
    val id: String,
    val categoryId: String,
    val nameTranslationKey: String,
    val descriptionTranslationKey: String,
    val labels: List<RuleCatalogueLabel> = emptyList(),
    val unavailableReasonTranslationKey: String? = null,
    val examples: List<RuleCatalogueExample> = emptyList(),
    val additionalCategoryIds: List<String> = emptyList(),
) {
    /** 條目所屬的全部分類，主要分類在前。 */
    val categoryIds: List<String> get() = listOf(categoryId) + additionalCategoryIds

    init {
        NamespacedId.requireValid(id) { "Invalid catalogue entry ID: $id" }
        categoryIds.forEach { NamespacedId.requireValid(it) { "Invalid catalogue entry category ID: $it" } }
        require(categoryIds.distinct().size == categoryIds.size) { "Duplicate catalogue entry category ID: $id" }
        require(nameTranslationKey.isNotBlank() && descriptionTranslationKey.isNotBlank()) { "Catalogue entry keys must not be blank" }
        require(unavailableReasonTranslationKey == null || unavailableReasonTranslationKey.isNotBlank()) { "Catalogue unavailable reason must not be blank" }
    }
}
