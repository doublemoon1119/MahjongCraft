package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

/** 目錄內容狀態，區分缺少資料與篩選後沒有結果。 */
enum class RuleCatalogueBrowseStatus {
    /** 沒有已知規則可供選擇。 */
    NO_RULES,

    /** 原設定無法取得，尚未選擇一般說明。 */
    CONFIG_UNAVAILABLE,

    /** 本地沒有所選規則的目錄來源。 */
    MISSING_PROVIDER,

    /** 目錄來源不支援指定的設定型別。 */
    UNSUPPORTED_CONFIG,

    /** 目錄來源存在但沒有提供任何條目。 */
    EMPTY_CATALOGUE,

    /** 目錄有條目，但目前搜尋或分類沒有符合項目。 */
    NO_RESULTS,

    /** 有可顯示的條目。 */
    AVAILABLE,
}

/**
 * 規則選擇的文字資料；缺少名稱時保留 ID，不排除沒有目錄的已知規則。
 *
 * @property ruleModuleId 穩定規則 ID。
 * @property displayName 當前語言的名稱，或缺少翻譯時的規則 ID。
 */
data class RuleCatalogueRuleOption(
    val ruleModuleId: String,
    val displayName: String,
)

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
