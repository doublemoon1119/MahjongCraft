package com.doublemoon1119.mahjongcraft.platform.fabric.network

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 一般客戶端→伺服器頻道使用的頻率與大小限制。
 *
 * 限制以正常操作的節奏為準並保留餘裕：連點操作面板或快速調整房間設定不會被丟棄，持續灌入要求才會。
 * 牌譜查詢依伺服器設定自行准入，不使用這裡的限制。
 */
internal object C2SRateLimits {
    /** 操作面板送出的決策選擇。 */
    val decisionSelection = C2SRateLimit.PerPlayer(burst = 10, refillInterval = 200.milliseconds)

    /** 等待室的房間操作，包含調整房間設定。 */
    val roomAction = C2SRateLimit.PerPlayer(burst = 10, refillInterval = 250.milliseconds)

    /** 會讓伺服器重建並送出完整快照的要求。 */
    val snapshotRequest = C2SRateLimit.PerPlayer(burst = 3, refillInterval = 2.seconds)

    /** 玩家偏好與自動操作的更新。 */
    val preferenceUpdate = C2SRateLimit.PerPlayer(burst = 10, refillInterval = 250.milliseconds)

    /** 房間操作 JSON 的 UTF-8 位元組上限，容納完整房間設定。 */
    const val ROOM_ACTION_BYTES: Int = 16 * 1024

    /** 決策選擇與自動操作更新 JSON 的 UTF-8 位元組上限。 */
    const val DECISION_SELECTION_BYTES: Int = 4 * 1024

    /** 不帶內容或只帶單一布林值的信號 JSON 的 UTF-8 位元組上限。 */
    const val SIGNAL_BYTES: Int = 64
}
