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
