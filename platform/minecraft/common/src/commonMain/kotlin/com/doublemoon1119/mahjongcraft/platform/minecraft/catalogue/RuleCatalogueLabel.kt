package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

/**
 * 條目的價值、限制或情境標籤，例如日麻的「門清限定」。
 *
 * @property nameTranslationKey 標籤文字的翻譯鍵。
 * @property descriptionTranslationKey 解釋標籤意思的翻譯鍵；標籤本身已足夠清楚時為 null。
 */
data class RuleCatalogueLabel(
    val nameTranslationKey: String,
    val descriptionTranslationKey: String? = null,
) {
    init {
        require(nameTranslationKey.isNotBlank()) { "Catalogue label key must not be blank" }
        require(descriptionTranslationKey == null || descriptionTranslationKey.isNotBlank()) { "Catalogue label description must not be blank" }
    }
}
