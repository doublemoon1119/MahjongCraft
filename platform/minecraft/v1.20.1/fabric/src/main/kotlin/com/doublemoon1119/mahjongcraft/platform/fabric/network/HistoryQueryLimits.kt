package com.doublemoon1119.mahjongcraft.platform.fabric.network

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 歷史查詢不可由設定停用的執行與線路大小限制。 */
internal object HistoryQueryLimits {
    /** 單次歷史查詢的最長執行時間。 */
    val timeout: Duration = 5.seconds

    /** 請求 JSON 的 UTF-8 位元組上限。 */
    const val REQUEST_BYTES: Int = 4 * 1024

    /** 回應 JSON 的 UTF-8 位元組上限。 */
    const val RESPONSE_BYTES: Int = 128 * 1024

    /** 配對識別字串的長度上限。 */
    const val REQUEST_ID_LENGTH: Int = 64

    /** 游標 JSON 字串長度上限，仍受完整要求的位元組上限約束。 */
    const val CURSOR_LENGTH: Int = 1536
}
