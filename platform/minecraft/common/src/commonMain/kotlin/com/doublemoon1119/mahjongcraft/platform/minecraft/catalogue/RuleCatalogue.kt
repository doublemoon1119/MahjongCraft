package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

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
