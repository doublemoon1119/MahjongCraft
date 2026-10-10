package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue
/**
 * 一次目錄瀏覽的唯讀快照；原目錄保留分類，條目僅按瀏覽條件篩選。
 *
 * @property ruleOptions 穩定排列的規則選項。
 * @property selectedRuleModuleId 所選規則 ID；沒有選項時為 null。
 * @property configSource 所選目錄的設定來源，不將一般說明標成原場設定。
 * @property categoryId 所選分類 ID；null 表示全部分類。
 * @property searchText 使用者輸入的搜尋文字，跨規則切換保留。
 * @property status 內容狀態。
 * @property catalogue 已解析的原目錄；無法解析時為 null。
 * @property entries 篩選後的條目，順序與來源一致。
 */
data class RuleCatalogueBrowseState(
    val ruleOptions: List<RuleCatalogueRuleOption>,
    val selectedRuleModuleId: String?,
    val configSource: RuleCatalogueConfigSource,
    val categoryId: String?,
    val searchText: String,
    val status: RuleCatalogueBrowseStatus,
    val catalogue: RuleCatalogue?,
    val entries: List<RuleCatalogueEntry>,
)
