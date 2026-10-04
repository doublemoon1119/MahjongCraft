package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry

/**
 * 客戶端目錄瀏覽狀態，僅選擇說明資料，不修改規則設定或權威狀態。
 *
 * 變更操作回傳是否生效，呼叫端據此重設內容捲動。文字解析在 [snapshot] 的邊界提供，
 * 因此更換語言後可重新取得篩選結果，不必重新呼叫目錄來源。
 *
 * @property catalogues 已完成註冊的目錄來源。
 * @property ruleNames 規則名稱翻譯鍵來源。
 * @param knownRuleIds 設定呈現等來源的已知規則 ID；建構時建立獨立快照。
 * @property context 開啟時的原規則與設定來源。
 */
class RuleCatalogueBrowser(
    private val catalogues: RuleCatalogueRegistry,
    private val ruleNames: RuleModuleDisplayNameRegistry,
    knownRuleIds: Collection<String>,
    private val context: RuleCatalogueBrowseContext = RuleCatalogueBrowseContext(),
) {
    /** 已登記目錄的獨立 ID 快照，供一般說明的初始選擇使用。 */
    private val catalogueRuleIds = catalogues.registrationKeys.toSet()

    /** 合併來源與原規則後的固定選項，按穩定 ID 排列。 */
    private val ruleIds = (knownRuleIds + catalogueRuleIds + listOfNotNull(context.ruleModuleId)).distinct().sorted().also { ids ->
        ids.forEach { id -> NamespacedId.requireValid(id) { "Invalid catalogue rule option ID: $id" } }
    }

    /** 開啟脈絡優先；一般說明優先選擇有目錄的規則，不寫死內建規則。 */
    private var selectedRuleId = context.ruleModuleId ?: ruleIds.firstOrNull { it in catalogueRuleIds } ?: ruleIds.firstOrNull()

    /** 是否明確選擇一般說明，不以原設定缺失暗示預設設定。 */
    private var generalExplanation = context.source == RuleCatalogueConfigSource.GENERAL

    /** null 表示全部分類。 */
    private var selectedCategoryId: String? = null

    /** 搜尋輸入的原文字。 */
    private var searchText: String = ""

    /** 僅在規則或設定來源變更時解析，不隨文字搜尋反覆呼叫 provider。 */
    private var resolution: RuleCatalogueResolution? = resolveSelection()

    /**
     * 切換至已知規則，重設分類但保留搜尋；切回原規則時恢復原設定。
     *
     * @param ruleModuleId 已知選項的規則 ID。
     * @return 是否變更所選規則；未知或相同選項為 false。
     */
    fun selectRule(ruleModuleId: String): Boolean {
        if (ruleModuleId !in ruleIds || ruleModuleId == selectedRuleId) return false
        val nextGeneral = ruleModuleId != context.ruleModuleId || context.source == RuleCatalogueConfigSource.GENERAL
        val nextResolution = resolveSelection(ruleModuleId, nextGeneral)
        selectedRuleId = ruleModuleId
        generalExplanation = nextGeneral
        selectedCategoryId = null
        resolution = nextResolution
        return true
    }

    /**
     * 明確改為所選規則的一般說明，可在原設定缺失或不支援時使用。
     *
     * @return 是否變更設定來源。
     */
    fun useGeneralExplanation(): Boolean {
        if (selectedRuleId == null || generalExplanation) return false
        val nextResolution = resolveSelection(selectedRuleId, true)
        generalExplanation = true
        selectedCategoryId = null
        resolution = nextResolution
        return true
    }

    /**
     * 在原規則上恢復開啟時的設定來源，包括無法取得設定的狀態。
     *
     * @return 是否恢復原設定來源；非原規則或一般說明入口為 false。
     */
    fun restoreOpeningConfig(): Boolean {
        if (selectedRuleId != context.ruleModuleId || context.source == RuleCatalogueConfigSource.GENERAL || !generalExplanation) return false
        val nextResolution = resolveSelection(selectedRuleId, false)
        generalExplanation = false
        selectedCategoryId = null
        resolution = nextResolution
        return true
    }

    /**
     * 選擇所選目錄的分類，不接受其他規則的分類。
     *
     * @param categoryId 分類 ID；null 表示全部分類。
     * @return 是否變更分類；未知或相同分類為 false。
     */
    fun selectCategory(categoryId: String?): Boolean {
        if (categoryId == selectedCategoryId) return false
        val catalogue = (resolution as? RuleCatalogueResolution.Available)?.catalogue
        if (categoryId != null && catalogue?.categories?.any { it.id == categoryId } != true) return false
        selectedCategoryId = categoryId
        return true
    }

    /**
     * 保存搜尋文字，不改變目錄來源或分類。
     *
     * @param text 搜尋輸入，實際比對時修剪前後空白並忽略大小寫。
     * @return 是否變更輸入。
     */
    fun setSearch(text: String): Boolean {
        if (text == searchText) return false
        searchText = text
        return true
    }

    /**
     * 以當前語言取得目錄快照；缺少翻譯時保留鍵與 ID 的搜尋能力。
     *
     * @param translate 翻譯鍵轉為純文字；缺少翻譯時回傳 null 或空白，不依賴平台文字類別。
     * @return 可供呈現的選項、設定來源、篩選條目與明確內容狀態。
     */
    fun snapshot(translate: (String) -> String?): RuleCatalogueBrowseState {
        val catalogue = (resolution as? RuleCatalogueResolution.Available)?.catalogue
        val query = searchText.trim()
        val entries = catalogue?.entries.orEmpty().filter { entry ->
            (selectedCategoryId == null || entry.categoryId == selectedCategoryId) &&
                (query.isEmpty() || searchableTexts(entry, translate).any { it.contains(query, ignoreCase = true) })
        }
        val status = when {
            selectedRuleId == null -> RuleCatalogueBrowseStatus.NO_RULES
            !generalExplanation && context.config == null -> RuleCatalogueBrowseStatus.CONFIG_UNAVAILABLE
            resolution == RuleCatalogueResolution.MissingProvider -> RuleCatalogueBrowseStatus.MISSING_PROVIDER
            resolution == RuleCatalogueResolution.UnsupportedConfig -> RuleCatalogueBrowseStatus.UNSUPPORTED_CONFIG
            catalogue?.entries.isNullOrEmpty() -> RuleCatalogueBrowseStatus.EMPTY_CATALOGUE
            entries.isEmpty() -> RuleCatalogueBrowseStatus.NO_RESULTS
            else -> RuleCatalogueBrowseStatus.AVAILABLE
        }
        return RuleCatalogueBrowseState(
            ruleOptions = ruleIds.map { id ->
                RuleCatalogueRuleOption(id, ruleNames.find(id)?.let { translate(it)?.takeIf(String::isNotBlank) } ?: id)
            },
            selectedRuleModuleId = selectedRuleId,
            configSource = if (generalExplanation) RuleCatalogueConfigSource.GENERAL else context.source,
            categoryId = selectedCategoryId,
            searchText = searchText,
            status = status,
            catalogue = catalogue,
            entries = entries,
        )
    }

    /**
     * 解析所選設定，原設定缺失時不呼叫預設來源。
     *
     * @param ruleId 欲解析的規則，預設為目前選擇。
     * @param useGeneral 是否採用該規則的一般說明設定，預設為目前來源。
     * @return 來源的解析結果；沒有規則或原設定缺失時為 null。
     */
    private fun resolveSelection(ruleId: String? = selectedRuleId, useGeneral: Boolean = generalExplanation): RuleCatalogueResolution? {
        val id = ruleId ?: return null
        if (!useGeneral && context.config == null) return null
        return catalogues.resolve(id, if (useGeneral) null else context.config)
    }
}

/**
 * 建立條目的搜尋文字，不改動 provider 的翻譯鍵或原順序。
 *
 * @param entry 欲比對的條目。
 * @param translate 當前語言解析器，缺少翻譯時回傳 null 或空白。
 * @return 條目 ID 及名稱、敘述、標籤的可搜尋文字。
 */
private fun searchableTexts(entry: RuleCatalogueEntry, translate: (String) -> String?): List<String> = listOf(entry.id) + (listOf(entry.nameTranslationKey, entry.descriptionTranslationKey) + entry.labelTranslationKeys).map { key ->
    translate(key)?.takeIf(String::isNotBlank) ?: key
}
