package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

/** 正式管理員重試連線的結果，不公開原始例外細節。 */
sealed interface HistoryRetryResult {
    /** 已重新連結並開始處理待寫事件。 */
    data object Reconnected : HistoryRetryResult

    /** 原本已連線，不建立第二個 writer。 */
    data object AlreadyConnected : HistoryRetryResult

    /** 開啟或驗證失敗，保留原資料。 */
    data object Failed : HistoryRetryResult
}
