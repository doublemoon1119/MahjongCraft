package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto

/** 客戶端歷史查詢的載入與配對結果。 */
sealed interface ClientHistoryQueryState {
    /** 尚未要求資料或原世界已解除。 */
    data object Idle : ClientHistoryQueryState

    /**
     * 等待最新要求。
     *
     * @property requestId 最新要求的配對鍵。
     */
    data class Loading(val requestId: String) : ClientHistoryQueryState

    /**
     * 最新清單的成功或穩定失敗回應。
     *
     * @property response 不含原始牌譜的清單結果。
     */
    data class ListResult(val response: HistoryListResponseDto) : ClientHistoryQueryState

    /**
     * 最新單場摘要的成功或穩定失敗回應。
     *
     * @property response 不含牌面內容的摘要結果。
     */
    data class SummaryResult(val response: HistorySummaryResponseDto) : ClientHistoryQueryState

    /**
     * 最新單場規則設定的成功或穩定失敗回應。
     *
     * @property response 不含重播內容的歷史規則設定結果。
     */
    data class RuleSettingsResult(val response: HistoryRuleSettingsResponseDto) : ClientHistoryQueryState

    /**
     * 最新單局事件頁的成功或穩定失敗回應。
     *
     * @property response 已通過 request context 配對的事件頁結果。
     */
    data class RoundEventsResult(val response: HistoryRoundEventsResponseDto) : ClientHistoryQueryState

    /**
     * 最新單局桌況的成功或穩定失敗回應。
     *
     * @property response 已通過 request context 配對的桌況結果。
     */
    data class RoundStateResult(val response: HistoryRoundStateResponseDto) : ClientHistoryQueryState

    /**
     * 封包未送出，不將失敗誤認為空清單。
     *
     * @property requestId 未送出的要求識別碼。
     */
    data class SendFailed(val requestId: String) : ClientHistoryQueryState
}
