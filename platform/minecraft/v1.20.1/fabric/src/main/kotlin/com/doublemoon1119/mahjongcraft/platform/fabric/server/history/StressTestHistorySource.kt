package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

/** 開發環境壓力測試資料庫的歷史資料來源；壓力測試建立資料庫時登記，刪除時清除。 */
class StressTestHistorySource {
    /** 目前的壓力測試資料來源；沒有壓力測試資料庫時為 null。 */
    @Volatile var current: HistoryDataSource? = null
}
