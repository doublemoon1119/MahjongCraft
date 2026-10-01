package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

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

    /** 封包未送出，不將失敗誤認為空清單。 */
    data object SendFailed : ClientHistoryQueryState
}

/**
 * 將歷史要求與反應式結果配對，不訂閱資料庫也不定期查詢。
 *
 * @property json 線路序列化設定。
 */
@Single
class ClientHistoryQueryCoordinator(private val json: Json) {
    /** 只接受目前連線最新要求的回應。 */
    private val correlation = HistoryQueryCorrelation()

    /** 主執行緒更新的最新結果。 */
    private val mutableState = MutableStateFlow<ClientHistoryQueryState>(ClientHistoryQueryState.Idle)

    /** 可供歷史呈現消費的唯讀狀態。 */
    val state: StateFlow<ClientHistoryQueryState> = mutableState.asStateFlow()

    /**
     * 送出新清單要求並使舊條件的回覆失效。
     *
     * @param request 查詢條件；配對 ID 由此處替換成唯一值。
     */
    fun queryList(request: HistoryListRequestDto) {
        val pending = request.copy(requestId = Uuid.random().toString())
        begin(pending.requestId) { MahjongChannels.historyListRequest.sendToServer(json, pending) }
    }

    /**
     * 送出單場摘要要求並使舊選取的回覆失效。
     *
     * @param request 目標對局與查閱範圍。
     */
    fun querySummary(request: HistorySummaryRequestDto) {
        val pending = request.copy(requestId = Uuid.random().toString())
        begin(pending.requestId) { MahjongChannels.historySummaryRequest.sendToServer(json, pending) }
    }

    /**
     * 套用最新清單回應；過期或重複回應不更新狀態。
     *
     * @param response 伺服器線路回應。
     */
    fun applyList(response: HistoryListResponseDto) {
        if (correlation.complete(response.requestId)) mutableState.value = ClientHistoryQueryState.ListResult(response)
    }

    /**
     * 套用最新摘要回應；過期或重複回應不更新狀態。
     *
     * @param response 伺服器線路回應。
     */
    fun applySummary(response: HistorySummaryResponseDto) {
        if (correlation.complete(response.requestId)) mutableState.value = ClientHistoryQueryState.SummaryResult(response)
    }

    /** 世界或連線切換時清除配對與資料，不接受原連線的回應。 */
    fun clear() {
        correlation.clear()
        mutableState.value = ClientHistoryQueryState.Idle
    }

    /**
     * 保存載入狀態後送出要求，失敗時撤回配對。
     *
     * @param requestId 此次唯一配對鍵。
     * @param send 實際線路傳送。
     */
    private fun begin(requestId: String, send: () -> Unit) {
        correlation.begin(requestId)
        mutableState.value = ClientHistoryQueryState.Loading(requestId)
        try {
            send()
        } catch (_: RuntimeException) {
            correlation.clear()
            mutableState.value = ClientHistoryQueryState.SendFailed
        }
    }
}
