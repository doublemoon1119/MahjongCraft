package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

/**
 * 具明確語意的牌面範例；情境條件由說明提供，不作為規則判定輸入。
 *
 * @property completeHand 是否為完整手牌；false 表示局部示意。
 * @property groups 依呈現順序排列的牌組。
 * @property descriptionTranslationKey 場風、自風或其他必要情境的說明翻譯鍵。
 * @property titleTranslationKey 接在範例種類之後的小標題翻譯鍵，例如「七對子」；null 時只顯示範例種類。
 */
data class RuleCatalogueExample(
    val completeHand: Boolean,
    val groups: List<RuleCatalogueTileGroup>,
    val descriptionTranslationKey: String? = null,
    val titleTranslationKey: String? = null,
) {
    init {
        require(groups.isNotEmpty()) { "Catalogue example must contain a tile group" }
        require(descriptionTranslationKey == null || descriptionTranslationKey.isNotBlank()) { "Catalogue example description must not be blank" }
        require(titleTranslationKey == null || titleTranslationKey.isNotBlank()) { "Catalogue example title must not be blank" }
    }
}
