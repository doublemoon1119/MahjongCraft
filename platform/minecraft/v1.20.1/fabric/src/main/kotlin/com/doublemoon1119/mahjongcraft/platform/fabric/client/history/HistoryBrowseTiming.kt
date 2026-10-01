package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 歷史瀏覽的條件合併與等待上限，不修改伺服器執行限制。 */
internal object HistoryBrowseTiming {
    /** 快速修改條件時，僅保留最後一次有效輸入。 */
    val conditionDebounce: Duration = 300.milliseconds

    /** 包含傳輸與伺服器執行的 client 等待上限。 */
    val responseTimeout: Duration = 10.seconds
}
