package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 在歷史畫面內監看單場保存狀態；PENDING 只以固定上限輪詢，不會無限佔用連線。
 *
 * @property transport 保存狀態查詢傳輸。
 * @property scope 此歷史 Screen 的獨立工作作用域。
 * @property pollInterval PENDING 狀態之間的等待時間。
 * @property maxPolls 最多送出的狀態查詢次數。
 * @property responseTimeout 單次查詢等待上限。
 * @property now 判斷查詢冷卻的單調時間來源。
 */
internal class HistoryArchiveStatusController(
    private val transport: HistoryArchiveStatusTransport,
    private val scope: CoroutineScope,
    private val pollInterval: Duration = 1.seconds,
    private val maxPolls: Int = 10,
    private val responseTimeout: Duration = 5.seconds,
    private val now: () -> Duration = { System.nanoTime().nanoseconds },
) {
    /** 供畫面觀察的狀態值。 */
    private val mutableView = MutableStateFlow<HistoryArchiveStatusView>(HistoryArchiveStatusView.Idle)

    /** 目前輪詢工作。 */
    private var monitor: Job? = null

    /** 尚未完成的查詢識別碼。 */
    private var activeRequestId: String? = null

    /** 目前監看的對局識別碼。 */
    private var target: String? = null

    /** 摘要查詢期間停止發出新的保存狀態要求。 */
    private var paused = false

    /** 最近一次保存狀態要求的傳送時間。 */
    private var lastSentAt: Duration? = null

    /** 建立監看時的連線工作階段版本。 */
    private var revision = transport.sessionRevision.value

    /** Screen 可觀察的目前保存狀態。 */
    val view: StateFlow<HistoryArchiveStatusView> = mutableView.asStateFlow()

    /** 開始監看指定場次；重複指定相同場次時，進行中的監看與已取得的最終結果都維持不變。 */
    fun watch(matchId: String?) {
        if (matchId == target && (monitor?.isActive == true || mutableView.value is HistoryArchiveStatusView.Resolved)) {
            paused = false
            return
        }
        stop()
        paused = false
        target = matchId
        revision = transport.sessionRevision.value
        if (matchId == null) {
            mutableView.value = HistoryArchiveStatusView.Idle
            return
        }
        mutableView.value = HistoryArchiveStatusView.Pending(matchId)
        monitor = scope.launch { poll(matchId) }
    }

    /** 取消畫面關閉時仍在途的查詢。 */
    fun close() {
        stop()
        paused = false
        target = null
        mutableView.value = HistoryArchiveStatusView.Idle
    }

    /** 重新開始目前指定對局的保存狀態查詢。 */
    fun retry() {
        watch(target)
    }

    /** 等待目前保存狀態查詢結束及冷卻，讓摘要查詢不與其爭用伺服器配額。 */
    suspend fun pause() {
        paused = true
        if (activeRequestId == null) {
            monitor?.cancel()
        } else {
            monitor?.join()
        }
        monitor = null
        val sentAt = lastSentAt ?: return
        delay((sentAt + transport.minimumInterval.value - now()).coerceAtLeast(Duration.ZERO))
    }

    /** 輪詢保存狀態直到取得終態或達到次數上限。
     *
     * @param matchId 欲查詢的對局識別碼。
     */
    private suspend fun poll(matchId: String) {
        repeat(maxPolls.coerceAtLeast(1)) {
            awaitInterval()
            if (paused || transport.sessionRevision.value != revision || target != matchId) return
            lastSentAt = now()
            val requestId = runCatching { transport.query(matchId) }.getOrNull()
            if (requestId == null) {
                if (target == matchId) mutableView.value = HistoryArchiveStatusView.Failed(matchId)
                return
            }
            activeRequestId = requestId
            val result = awaitResult(requestId, matchId)
            activeRequestId = null
            if (result == null) transport.cancel(requestId)
            if (result == null) return
            mutableView.value = if (result == HistoryArchiveStatusDto.PENDING) {
                HistoryArchiveStatusView.Pending(matchId)
            } else {
                HistoryArchiveStatusView.Resolved(result)
            }
            if (result != HistoryArchiveStatusDto.PENDING) return
            if (paused) return
        }
        if (target == matchId) mutableView.value = HistoryArchiveStatusView.Failed(matchId)
    }

    /** 等待輪詢間隔，並在重新載入提高冷卻時延長等待，避免沿用舊值提早送出。 */
    private suspend fun awaitInterval() {
        var waited = Duration.ZERO
        while (true) {
            val remaining = maxOf(pollInterval, transport.minimumInterval.value) - waited
            if (remaining <= Duration.ZERO) return
            delay(remaining)
            waited += remaining
        }
    }

    /** 等待與指定要求完全配對的回覆。
     *
     * @param requestId 要求識別碼。
     * @param matchId 對局識別碼，用於避免過期畫面更新狀態。
     * @return 成功回覆的保存狀態；逾時或錯誤時為 null。
     */
    private suspend fun awaitResult(requestId: String, matchId: String): HistoryArchiveStatusDto? {
        val state = withTimeoutOrNull(responseTimeout) {
            transport.state.first { state ->
                when (state) {
                    is ClientHistoryArchiveStatusState.Result -> state.response.requestId == requestId
                    is ClientHistoryArchiveStatusState.SendFailed -> state.requestId == requestId
                    else -> false
                }
            }
        }
        val response = (state as? ClientHistoryArchiveStatusState.Result)?.response
        val result = response?.takeIf { it.errorCode == null }?.status
        if (result == null && target == matchId) mutableView.value = HistoryArchiveStatusView.Failed(matchId)
        return result
    }

    /** 停止輪詢並取消尚未完成的傳輸要求。 */
    private fun stop() {
        monitor?.cancel()
        monitor = null
        activeRequestId?.let(transport::cancel)
        activeRequestId = null
    }
}
