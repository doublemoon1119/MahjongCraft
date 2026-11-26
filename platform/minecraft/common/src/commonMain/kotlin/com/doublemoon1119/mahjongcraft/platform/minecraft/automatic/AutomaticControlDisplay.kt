package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

/**
 * 一項自動操作在 Minecraft 中使用的名稱、說明及排列順序。
 *
 * @property labelTranslationKey 自動操作名稱的翻譯鍵。
 * @property descriptionTranslationKey 自動操作說明的翻譯鍵。
 * @property displayOrder 自動操作在清單中的排序值。
 */
data class AutomaticControlDisplay(
    val labelTranslationKey: String,
    val descriptionTranslationKey: String,
    val displayOrder: Int,
) {
    init {
        require(labelTranslationKey.isNotBlank()) { "Automatic control label translation key must not be blank" }
        require(descriptionTranslationKey.isNotBlank()) { "Automatic control description translation key must not be blank" }
    }
}
