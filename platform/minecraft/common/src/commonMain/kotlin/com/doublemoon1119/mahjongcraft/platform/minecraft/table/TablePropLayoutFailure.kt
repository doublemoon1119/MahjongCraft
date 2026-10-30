package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/** 桌面物件配置失敗的原因。 */
enum class TablePropLayoutFailure {
    /** 規則提供的識別碼、內容、座位位置或角度無效，或座位物件彼此重疊。 */
    INVALID_PLACEMENT,

    /** 中央區域放不下這個物件。 */
    NO_SPACE,

    /** 物件數量或搜尋位置的工作量超過上限。 */
    LIMIT_EXCEEDED,
}
