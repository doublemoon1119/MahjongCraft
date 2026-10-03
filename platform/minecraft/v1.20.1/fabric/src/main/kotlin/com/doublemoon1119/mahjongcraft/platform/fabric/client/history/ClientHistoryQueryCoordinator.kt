package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import kotlin.time.Duration
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

    /** 最新單場規則設定的成功或穩定失敗回應。
     * @property response 不含重播內容的歷史規則設定結果。
     */
    data class RuleSettingsResult(val response: HistoryRuleSettingsResponseDto) : ClientHistoryQueryState

    /** 最新單局事件頁的成功或穩定失敗回應。
     * @property response 已通過 request context 配對的事件頁結果。
     */
    data class RoundEventsResult(val response: HistoryRoundEventsResponseDto) : ClientHistoryQueryState

    /** 最新單局桌況的成功或穩定失敗回應。
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

/**
 * 將歷史要求與反應式結果配對，不訂閱資料庫也不定期查詢。
 *
 * @property sender 實際傳送線路 DTO 的平台邊界。
 * @property settings 目前連線伺服器公布的查詢間隔。
 */
@Single(binds = [HistoryQueryTransport::class])
class ClientHistoryQueryCoordinator(
    private val sender: HistoryQuerySender,
    private val settings: ClientHistoryQuerySettings,
) : HistoryQueryTransport {
    /** 只接受目前連線最新要求的回應。 */
    private val correlation = HistoryQueryCorrelation()

    /** 目前單局事件要求的內容選擇器，用於拒絕同 request ID 的錯誤回覆。 */
    private var pendingRoundEvents: HistoryRoundEventsRequestDto? = null

    /** 目前單局桌況要求的內容選擇器，用於拒絕同 request ID 的錯誤回覆。 */
    private var pendingRoundState: HistoryRoundStateRequestDto? = null

    /** 主執行緒更新的最新結果。 */
    private val mutableState = MutableStateFlow<ClientHistoryQueryState>(ClientHistoryQueryState.Idle)

    /** 目前連線／世界工作階段版本。 */
    private val mutableSessionRevision = MutableStateFlow(0L)

    /** 可供歷史呈現消費的唯讀狀態。 */
    override val state: StateFlow<ClientHistoryQueryState> = mutableState.asStateFlow()

    /** 可供外部辨識工作階段變更的唯讀版本。 */
    override val sessionRevision: StateFlow<Long> = mutableSessionRevision.asStateFlow()

    /** 目前連線有效的查詢冷卻。 */
    override val minimumInterval: StateFlow<Duration> = settings.minimumInterval

    /**
     * 送出新清單要求並使舊條件的回覆失效。
     *
     * @param request 查詢條件；配對 ID 由此處替換成唯一值。
     */
    override fun queryList(request: HistoryListRequestDto): String {
        val pending = request.copy(requestId = Uuid.random().toString())
        return begin(pending.requestId, HistoryQueryKind.LIST) { sender.sendList(pending) }
    }

    /**
     * 送出單場摘要要求並使舊選取的回覆失效。
     *
     * @param request 目標對局與查閱範圍。
     */
    override fun querySummary(request: HistorySummaryRequestDto): String {
        val pending = request.copy(requestId = Uuid.random().toString())
        return begin(pending.requestId, HistoryQueryKind.SUMMARY) { sender.sendSummary(pending) }
    }

    /** 送出單場規則設定要求並使舊選取的回覆失效。
     * @param request 目標對局與查閱範圍。
     * @return 此次要求的配對識別碼。
     */
    override fun queryRuleSettings(request: HistoryRuleSettingsRequestDto): String {
        val pending = request.copy(requestId = Uuid.random().toString())
        return begin(pending.requestId, HistoryQueryKind.RULE_SETTINGS) { sender.sendRuleSettings(pending) }
    }

    /** 傳送單局事件頁要求。
     * @param request 目標對局、局序號及事件頁選擇器。
     * @return 此次要求的配對識別碼。
     */
    override fun queryRoundEvents(request: HistoryRoundEventsRequestDto): String {
        val pending = request.copy(requestId = Uuid.random().toString())
        pendingRoundEvents = pending
        pendingRoundState = null
        return begin(pending.requestId, HistoryQueryKind.ROUND_EVENTS) { sender.sendRoundEvents(pending) }
    }

    /** 傳送單局桌況要求。
     * @param request 目標對局、局序號及局內位置。
     * @return 此次要求的配對識別碼。
     */
    override fun queryRoundState(request: HistoryRoundStateRequestDto): String {
        val pending = request.copy(requestId = Uuid.random().toString())
        pendingRoundState = pending
        pendingRoundEvents = null
        return begin(pending.requestId, HistoryQueryKind.ROUND_STATE) { sender.sendRoundState(pending) }
    }

    /**
     * 套用最新清單回應；過期或重複回應不更新狀態。
     *
     * @param response 伺服器線路回應。
     */
    fun applyList(response: HistoryListResponseDto) {
        if (correlation.complete(response.requestId, HistoryQueryKind.LIST)) {
            mutableState.value = ClientHistoryQueryState.ListResult(response)
        }
    }

    /**
     * 套用最新摘要回應；過期或重複回應不更新狀態。
     *
     * @param response 伺服器線路回應。
     */
    fun applySummary(response: HistorySummaryResponseDto) {
        if (correlation.complete(response.requestId, HistoryQueryKind.SUMMARY)) {
            mutableState.value = ClientHistoryQueryState.SummaryResult(response)
        }
    }

    /** 套用最新規則設定回應；過期或重複回應不更新狀態。
     * @param response 伺服器線路回應。
     */
    fun applyRuleSettings(response: HistoryRuleSettingsResponseDto) {
        if (correlation.complete(response.requestId, HistoryQueryKind.RULE_SETTINGS)) {
            mutableState.value = ClientHistoryQueryState.RuleSettingsResult(response)
        }
    }

    /** 套用目前單局事件回覆；request ID 與查詢種類不符時忽略。
     * @param response 伺服器線路回應。
     */
    fun applyRoundEvents(response: HistoryRoundEventsResponseDto) {
        val expected = pendingRoundEvents
        if (expected != null &&
            response.requestId == expected.requestId &&
            response.matchId == expected.matchId &&
            response.roundNumber == expected.roundNumber &&
            response.startTransactionIndex == expected.startTransactionIndex &&
            correlation.complete(response.requestId, HistoryQueryKind.ROUND_EVENTS)
        ) {
            pendingRoundEvents = null
            mutableState.value = ClientHistoryQueryState.RoundEventsResult(response)
        }
    }

    /** 套用目前單局桌況回覆；request ID 與查詢種類不符時忽略。
     * @param response 伺服器線路回應。
     */
    fun applyRoundState(response: HistoryRoundStateResponseDto) {
        val expected = pendingRoundState
        if (expected != null &&
            response.requestId == expected.requestId &&
            response.matchId == expected.matchId &&
            response.roundNumber == expected.roundNumber &&
            response.position == expected.position &&
            correlation.complete(response.requestId, HistoryQueryKind.ROUND_STATE)
        ) {
            pendingRoundState = null
            mutableState.value = ClientHistoryQueryState.RoundStateResult(response)
        }
    }

    /**
     * 只取消識別碼相符的待回應要求，不推進工作階段版本。
     *
     * @param requestId 要取消的要求識別碼。
     * @return 是否取消了目前仍待回應且識別碼相符的要求。
     */
    override fun cancel(requestId: String): Boolean {
        if (!correlation.cancel(requestId)) return false
        pendingRoundEvents = null
        pendingRoundState = null
        mutableState.value = ClientHistoryQueryState.Idle
        return true
    }

    /** 世界或連線切換時清除配對與資料，不接受原連線的回應。 */
    fun clear() {
        settings.reset()
        correlation.clear()
        pendingRoundEvents = null
        pendingRoundState = null
        mutableSessionRevision.value += 1L
        mutableState.value = ClientHistoryQueryState.Idle
    }

    /**
     * 保存載入狀態後送出要求，失敗時撤回配對。
     *
     * @param requestId 此次唯一配對鍵。
     * @param kind 此次要求期待的回應種類。
     * @param send 實際線路傳送。
     * @return 此次要求的配對識別碼。
     */
    private fun begin(requestId: String, kind: HistoryQueryKind, send: () -> Unit): String {
        if (kind != HistoryQueryKind.ROUND_EVENTS) pendingRoundEvents = null
        if (kind != HistoryQueryKind.ROUND_STATE) pendingRoundState = null
        correlation.begin(requestId, kind)
        mutableState.value = ClientHistoryQueryState.Loading(requestId)
        try {
            send()
        } catch (_: RuntimeException) {
            correlation.cancel(requestId)
            pendingRoundEvents = null
            pendingRoundState = null
            mutableState.value = ClientHistoryQueryState.SendFailed(requestId)
        }
        return requestId
    }
}
