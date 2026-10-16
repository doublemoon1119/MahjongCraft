package com.doublemoon1119.mahjongcraft.platform.fabric.network

import kotlin.time.Duration

/** 客戶端→伺服器頻道對每位玩家要求的頻率限制；每個頻道建立時都必須宣告。 */
sealed interface C2SRateLimit {
    /**
     * 每位玩家最多連續送出 [burst] 個要求，之後每經過 [refillInterval] 恢復一個名額；超過的要求直接丟棄。
     *
     * @property burst 連續要求的上限。
     * @property refillInterval 恢復一個名額所需的時間。
     */
    data class PerPlayer(val burst: Int, val refillInterval: Duration) : C2SRateLimit {
        init {
            require(burst > 0) { "Rate limit burst must be positive" }
            require(refillInterval.isPositive()) { "Rate limit refill interval must be positive" }
        }
    }

    /** 由接收端自行做准入控制，例如依伺服器設定限制頻率與同時工作數的牌譜查詢。 */
    data object HandledByReceiver : C2SRateLimit
}
