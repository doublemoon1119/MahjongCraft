package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

/** 世界中保存麻將資料的來源；決定讀取失敗時停用的範圍與恢復建議。 */
enum class MahjongDataSource {
    /** 房間、對局與尚未寫入牌譜資料庫的事件；無法讀取時停用整個麻將功能。 */
    AUTHORITATIVE_STATE,

    /** 桌子位置索引；可從載入的 chunk 重新建立，無法讀取時改從空索引開始。 */
    TABLE_LOCATIONS,

    /** 牌譜資料庫；無法開啟時暫停牌譜寫入與查閱，對局照常進行。 */
    HISTORY_DATABASE,
}
