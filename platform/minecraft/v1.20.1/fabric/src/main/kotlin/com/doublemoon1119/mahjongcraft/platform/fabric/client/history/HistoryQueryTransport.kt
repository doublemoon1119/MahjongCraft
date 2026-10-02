package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** 客戶端歷史查詢的傳輸與生命週期邊界。 */
interface HistoryQueryTransport {
    /** 可供呈現層消費的最新查詢狀態。 */
    val state: StateFlow<ClientHistoryQueryState>

    /** 目前連線／世界工作階段的單調遞增版本。 */
    val sessionRevision: StateFlow<Long>

    /** 目前伺服器公布的最短查詢間隔。 */
    val minimumInterval: StateFlow<Duration>

    /**
     * 傳送歷史清單要求。
     *
     * @param request 查詢條件；傳輸實作會產生唯一配對識別碼。
     * @return 此次要求的配對識別碼。
     */
    fun queryList(request: HistoryListRequestDto): String

    /**
     * 傳送單場摘要要求。
     *
     * @param request 目標對局與查閱範圍；傳輸實作會產生唯一配對識別碼。
     * @return 此次要求的配對識別碼。
     */
    fun querySummary(request: HistorySummaryRequestDto): String

    /** 傳送單場歷史規則設定要求。
     * @param request 目標對局與查閱範圍。
     * @return 此次要求的配對識別碼。
     */
    fun queryRuleSettings(request: HistoryRuleSettingsRequestDto): String

    /**
     * 取消指定的待回應要求。
     *
     * @param requestId 要取消的配對識別碼。
     * @return 是否取消了目前仍待回應且識別碼相符的要求。
     */
    fun cancel(requestId: String): Boolean
}
