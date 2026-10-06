package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

/** 規則一覽畫面使用的集中式翻譯鍵。 */
object MinecraftRuleCatalogueScreenKeys {
    /** 規則一覽標題。 */
    const val TITLE: String = "mahjongcraft.rule_catalogue.screen.title"

    /** 規則切換按鈕文字，參數為目前規則名稱。 */
    const val RULE_BUTTON: String = "mahjongcraft.rule_catalogue.screen.rule_button"

    /** 規則切換按鈕提示的說明。 */
    const val RULE_TOOLTIP: String = "mahjongcraft.rule_catalogue.screen.rule_tooltip"

    /** 分類切換按鈕文字，參數為目前分類名稱。 */
    const val CATEGORY_BUTTON: String = "mahjongcraft.rule_catalogue.screen.category_button"

    /** 分類切換按鈕提示的說明。 */
    const val CATEGORY_TOOLTIP: String = "mahjongcraft.rule_catalogue.screen.category_tooltip"

    /** 顯示全部分類的選項。 */
    const val ALL_CATEGORIES: String = "mahjongcraft.rule_catalogue.screen.all_categories"

    /** 搜尋輸入框的名稱。 */
    const val SEARCH: String = "mahjongcraft.rule_catalogue.screen.search"

    /** 搜尋欄位空白時的輸入提示。 */
    const val SEARCH_HINT: String = "mahjongcraft.rule_catalogue.screen.search_hint"

    /** 搜尋欄位提示。 */
    const val SEARCH_TOOLTIP: String = "mahjongcraft.rule_catalogue.screen.search_tooltip"

    /** 清除搜尋按鈕。 */
    const val CLEAR: String = "mahjongcraft.rule_catalogue.screen.clear"

    /** 關閉畫面按鈕。 */
    const val CLOSE: String = "mahjongcraft.rule_catalogue.screen.close"

    /** 返回按鈕。 */
    const val BACK: String = "mahjongcraft.rule_catalogue.screen.back"

    /** 設定來源切換按鈕文字，參數為目前設定來源名稱。 */
    const val SOURCE_BUTTON: String = "mahjongcraft.rule_catalogue.screen.source_button"

    /** 設定來源切換按鈕提示的說明。 */
    const val SOURCE_TOOLTIP: String = "mahjongcraft.rule_catalogue.screen.source_tooltip"

    /** 切換到其他規則後無法選擇原設定的說明。 */
    const val SOURCE_OTHER_RULE: String = "mahjongcraft.rule_catalogue.screen.source_other_rule"

    /** 依一般說明顯示、不套用對局設定的說明。 */
    const val GENERAL_NOTE: String = "mahjongcraft.rule_catalogue.screen.general_note"

    /** 一般說明的設定來源名稱。 */
    const val SOURCE_GENERAL: String = "mahjongcraft.rule_catalogue.screen.source.general"

    /** 房間設定的設定來源名稱。 */
    const val SOURCE_ROOM: String = "mahjongcraft.rule_catalogue.screen.source.room"

    /** 尚未套用的房間設定草稿的設定來源名稱。 */
    const val SOURCE_ROOM_DRAFT: String = "mahjongcraft.rule_catalogue.screen.source.room_draft"

    /** 歷史對局設定的設定來源名稱。 */
    const val SOURCE_HISTORY: String = "mahjongcraft.rule_catalogue.screen.source.history"

    /** 選項提示中目前選項的格式，參數為選項名稱。 */
    const val TOOLTIP_CURRENT: String = "mahjongcraft.rule_catalogue.screen.tooltip.current"

    /** 選項提示中選項清單的標題。 */
    const val TOOLTIP_OPTIONS: String = "mahjongcraft.rule_catalogue.screen.tooltip.options"

    /** 沒有規則可顯示的狀態。 */
    const val STATUS_NO_RULES: String = "mahjongcraft.rule_catalogue.screen.status.no_rules"

    /** 規則設定無法取得的狀態。 */
    const val STATUS_CONFIG_UNAVAILABLE: String = "mahjongcraft.rule_catalogue.screen.status.config_unavailable"

    /** 本地沒有該規則說明來源的狀態。 */
    const val STATUS_MISSING_PROVIDER: String = "mahjongcraft.rule_catalogue.screen.status.missing_provider"

    /** 不支援目前設定的狀態。 */
    const val STATUS_UNSUPPORTED_CONFIG: String = "mahjongcraft.rule_catalogue.screen.status.unsupported_config"

    /** 目錄為空的狀態。 */
    const val STATUS_EMPTY_CATALOGUE: String = "mahjongcraft.rule_catalogue.screen.status.empty_catalogue"

    /** 搜尋沒有結果的狀態。 */
    const val STATUS_NO_RESULTS: String = "mahjongcraft.rule_catalogue.screen.status.no_results"

    /** 完整手牌範例的標題。 */
    const val EXAMPLE_COMPLETE: String = "mahjongcraft.rule_catalogue.screen.example.complete"

    /** 局部示意範例的標題。 */
    const val EXAMPLE_PARTIAL: String = "mahjongcraft.rule_catalogue.screen.example.partial"

    /** 手牌群組標籤。 */
    const val GROUP_HAND: String = "mahjongcraft.rule_catalogue.screen.group.hand"

    /** 和牌張群組標籤。 */
    const val GROUP_WINNING_TILE: String = "mahjongcraft.rule_catalogue.screen.group.winning_tile"

    /** 副露群組標籤。 */
    const val GROUP_OPEN_MELD: String = "mahjongcraft.rule_catalogue.screen.group.open_meld"

    /** 明槓群組標籤。 */
    const val GROUP_OPEN_KAN: String = "mahjongcraft.rule_catalogue.screen.group.open_kan"

    /** 暗槓群組標籤。 */
    const val GROUP_CLOSED_KAN: String = "mahjongcraft.rule_catalogue.screen.group.closed_kan"

    /** 示意牌群組標籤。 */
    const val GROUP_ILLUSTRATION: String = "mahjongcraft.rule_catalogue.screen.group.illustration"

    /** 缺少翻譯時的提示，參數為翻譯鍵。 */
    const val MISSING_TRANSLATION: String = "mahjongcraft.rule_catalogue.screen.missing_translation"

    /** 缺少牌面圖案時的提示，參數為牌種。 */
    const val MISSING_ASSET: String = "mahjongcraft.rule_catalogue.screen.missing_asset"

    /** 所有規則一覽畫面翻譯鍵。 */
    val ALL: Set<String> = setOf(
        TITLE, RULE_BUTTON, RULE_TOOLTIP, CATEGORY_BUTTON, CATEGORY_TOOLTIP, ALL_CATEGORIES, SEARCH, SEARCH_HINT,
        SEARCH_TOOLTIP, CLEAR, CLOSE, BACK, SOURCE_BUTTON, SOURCE_TOOLTIP, SOURCE_OTHER_RULE, GENERAL_NOTE,
        SOURCE_GENERAL, SOURCE_ROOM, SOURCE_ROOM_DRAFT, SOURCE_HISTORY, TOOLTIP_CURRENT, TOOLTIP_OPTIONS,
        STATUS_NO_RULES, STATUS_CONFIG_UNAVAILABLE, STATUS_MISSING_PROVIDER, STATUS_UNSUPPORTED_CONFIG,
        STATUS_EMPTY_CATALOGUE, STATUS_NO_RESULTS, EXAMPLE_COMPLETE, EXAMPLE_PARTIAL, GROUP_HAND,
        GROUP_WINNING_TILE, GROUP_OPEN_MELD, GROUP_OPEN_KAN, GROUP_CLOSED_KAN, GROUP_ILLUSTRATION,
        MISSING_TRANSLATION, MISSING_ASSET,
    )
}
