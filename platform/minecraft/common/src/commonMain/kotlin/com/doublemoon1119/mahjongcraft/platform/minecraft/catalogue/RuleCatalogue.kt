package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 規則的一覽說明資料；列表順序即呈現順序，不參與計分或和牌判定。
 *
 * @property categories 規則自訂分類，不限制翻數或計分單位。
 * @property entries 說明條目，包含役種、加算項目或流局說明。
 */
data class RuleCatalogue(
    val categories: List<RuleCatalogueCategory>,
    val entries: List<RuleCatalogueEntry>,
) {
    init {
        require(categories.map { it.id }.distinct().size == categories.size) { "Duplicate catalogue category ID" }
        require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate catalogue entry ID" }
        require(entries.all { entry -> entry.categoryIds.all { id -> categories.any { it.id == id } } }) { "Unknown catalogue category" }
    }
}

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

/**
 * 範例中的獨立牌組，沿用數牌、字牌及帶有穩定 ID 的擴充牌模型。
 *
 * @property role 牌組語意，不依位置推測和牌張或副露。
 * @property tiles 牌種排列順序。
 */
data class RuleCatalogueTileGroup(
    val role: RuleCatalogueTileGroupRole,
    val tiles: List<Tile>,
) {
    init {
        require(tiles.isNotEmpty()) { "Catalogue tile group must not be empty" }
        require(role != RuleCatalogueTileGroupRole.WINNING_TILE || tiles.size == 1) { "Winning tile group must contain exactly one tile" }
    }
}

/** 範例牌組的呈現語意，不表示權威副露狀態。 */
enum class RuleCatalogueTileGroupRole {
    /** 立牌。 */
    HAND,

    /** 獨立和牌張。 */
    WINNING_TILE,

    /** 吃碰等公開副露。 */
    OPEN_MELD,

    /** 明槓或加槓。 */
    OPEN_KAN,

    /** 暗槓。 */
    CLOSED_KAN,

    /** 不代表完整手牌結構的示意牌組。 */
    ILLUSTRATION,
}
