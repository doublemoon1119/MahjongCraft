package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** 客戶端保存狀態查詢的結果。 */
internal sealed interface ClientHistoryArchiveStatusState {
    /** 尚未要求保存狀態。 */
    data object Idle : ClientHistoryArchiveStatusState

    /** 等待單一狀態回覆。
     *
     * @property requestId 此次查詢的配對識別碼。
     * @property matchId 正在查詢的對局識別碼。
     */
    data class Loading(val requestId: String, val matchId: String) : ClientHistoryArchiveStatusState

    /** 已收到與目前要求配對的保存狀態。
     *
     * @property response 伺服器回覆的保存狀態。
     */
    data class Result(val response: HistoryArchiveStatusResponseDto) : ClientHistoryArchiveStatusState

    /** 客戶端無法送出要求。
     *
     * @property requestId 未送出的要求識別碼。
     * @property matchId 無法查詢的對局識別碼。
     */
    data class SendFailed(val requestId: String, val matchId: String) : ClientHistoryArchiveStatusState
}

/** 保存狀態查詢的 client 傳輸邊界。 */
internal interface HistoryArchiveStatusTransport {
    /** 最近一次狀態查詢結果。 */
    val state: StateFlow<ClientHistoryArchiveStatusState>

    /** 目前連線／世界工作階段版本。 */
    val sessionRevision: StateFlow<Long>

    /** 目前伺服器公布的最短查詢間隔。 */
    val minimumInterval: StateFlow<Duration>

    /** 傳送指定對局的保存狀態要求。
     *
     * @param matchId 對局識別碼。
     * @return 新要求的識別碼。
     */
    fun query(matchId: String): String

    /** 取消指定的狀態要求。
     *
     * @param requestId 要取消的要求識別碼。
     * @return 是否取消了有效要求。
     */
    fun cancel(requestId: String): Boolean
}

/**
 * 將保存狀態 DTO 與 request ID 配對，避免舊連線回覆污染目前 Screen。
 *
 * @property sender 實際傳送保存狀態要求的邊界。
 * @param settings 目前連線伺服器公布的查詢間隔。
 */
@Single(binds = [HistoryArchiveStatusTransport::class])
internal class ClientHistoryArchiveStatusCoordinator(
    private val sender: HistoryArchiveStatusSender,
    settings: ClientHistoryQuerySettings,
) : HistoryArchiveStatusTransport {
    /** 只保留目前連線有效的要求配對。 */
    private val correlation = HistoryArchiveStatusCorrelation()

    /** 主執行緒更新的保存狀態。 */
    private val mutableState = MutableStateFlow<ClientHistoryArchiveStatusState>(ClientHistoryArchiveStatusState.Idle)

    /** 可使原連線的所有要求失效的世代。 */
    private val mutableSessionRevision = MutableStateFlow(0L)

    /** 呈現層可觀察的保存狀態。 */
    override val state: StateFlow<ClientHistoryArchiveStatusState> = mutableState.asStateFlow()

    /** 呈現層可辨識的連線世代。 */
    override val sessionRevision: StateFlow<Long> = mutableSessionRevision.asStateFlow()

    /** 保存狀態輪詢與列表共用的伺服器冷卻。 */
    override val minimumInterval: StateFlow<Duration> = settings.minimumInterval

    /** 建立並傳送指定對局的保存狀態要求。
     *
     * @param matchId 對局識別碼。
     * @return 新要求的識別碼。
     */
    override fun query(matchId: String): String {
        val requestId = Uuid.random().toString()
        val request = HistoryArchiveStatusRequestDto(requestId, matchId)
        correlation.begin(requestId, matchId)
        mutableState.value = ClientHistoryArchiveStatusState.Loading(requestId, matchId)
        try {
            sender.send(request)
        } catch (_: RuntimeException) {
            correlation.cancel(requestId)
            mutableState.value = ClientHistoryArchiveStatusState.SendFailed(requestId, matchId)
        }
        return requestId
    }

    /** 套用目前要求的伺服器回覆。
     *
     * @param response 已解碼的保存狀態回覆。
     */
    fun apply(response: HistoryArchiveStatusResponseDto) {
        if (correlation.complete(response.requestId)) mutableState.value = ClientHistoryArchiveStatusState.Result(response)
    }

    /** 取消目前仍有效的要求。
     *
     * @param requestId 要求識別碼。
     * @return 是否確實取消了目前要求。
     */
    override fun cancel(requestId: String): Boolean {
        if (!correlation.cancel(requestId)) return false
        mutableState.value = ClientHistoryArchiveStatusState.Idle
        return true
    }

    /** 連線切換時使舊 request 全部失效。 */
    fun clear() {
        correlation.clear()
        mutableSessionRevision.value += 1L
        mutableState.value = ClientHistoryArchiveStatusState.Idle
    }
}

/** 保存狀態要求的唯一配對記錄。 */
internal class HistoryArchiveStatusCorrelation {
    /** 目前有效的要求與對局識別碼配對。 */
    private var active: Pair<String, String>? = null

    /** 記錄新的要求與對局配對。
     *
     * @param requestId 要求識別碼。
     * @param matchId 對局識別碼。
     */
    fun begin(requestId: String, matchId: String) {
        active = requestId to matchId
    }

    /** 完成指定要求並清除配對。
     *
     * @param requestId 回覆中的要求識別碼。
     * @return 回覆是否與目前要求配對。
     */
    fun complete(requestId: String): Boolean {
        if (active?.first != requestId) return false
        active = null
        return true
    }

    /** 取消指定要求並清除配對。
     *
     * @param requestId 要求識別碼。
     * @return 要求是否仍是目前有效要求。
     */
    fun cancel(requestId: String): Boolean {
        if (active?.first != requestId) return false
        active = null
        return true
    }

    /** 清除所有工作階段中的要求配對。 */
    fun clear() {
        active = null
    }
}

/** 將保存狀態要求交給 Minecraft 平台網路頻道。 */
internal interface HistoryArchiveStatusSender {
    /** 傳送已帶有 request ID 的保存狀態 DTO。
     *
     * @param request 具備配對識別碼的狀態要求。
     */
    fun send(request: HistoryArchiveStatusRequestDto)
}

/** Screen 可觀察的保存狀態；不把伺服器內部錯誤暴露給呈現層。 */
internal sealed interface HistoryArchiveStatusView {
    /** 尚未指定對局。 */
    data object Idle : HistoryArchiveStatusView

    /** 等待伺服器確認保存狀態。
     *
     * @property matchId 正在等待的對局識別碼。
     */
    data class Pending(val matchId: String) : HistoryArchiveStatusView

    /** 已取得穩定保存狀態。
     *
     * @property status 伺服器判定的保存狀態。
     */
    data class Resolved(val status: HistoryArchiveStatusDto) : HistoryArchiveStatusView

    /** 客戶端等待或傳輸失敗。
     *
     * @property matchId 查詢失敗的對局識別碼。
     */
    data class Failed(val matchId: String) : HistoryArchiveStatusView
}
