package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

/**
 * 具明確語意的牌面範例；情境條件由說明提供，不作為規則判定輸入。
 *
 * @property completeHand 是否為完整手牌；false 表示局部示意。
 * @property groups 依呈現順序排列的牌組。
 * @property descriptionTranslationKey 場風、自風或其他必要情境的說明翻譯鍵。
 */
data class RuleCatalogueExample(
    val completeHand: Boolean,
    val groups: List<RuleCatalogueTileGroup>,
    val descriptionTranslationKey: String? = null,
) {
    init {
        require(groups.isNotEmpty()) { "Catalogue example must contain a tile group" }
        require(descriptionTranslationKey == null || descriptionTranslationKey.isNotBlank()) { "Catalogue example description must not be blank" }
    }
}
