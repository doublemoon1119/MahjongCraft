package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * 歷史查詢共用的有界執行流程；各端點保留資料查詢、授權政策與回應內容。
 *
 * @property scope 執行查詢的作用域。
 * @property dispatchers 提供回覆時使用的伺服器主執行緒。
 * @property admission 管理全域與個別玩家的查詢名額。
 * @property timeout 單次查詢的最長等待時間。
 */
internal class HistoryQueryExecution(
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val admission: HistoryQueryAdmission,
    private val timeout: Duration,
) {
    /**
     * 取得名額後啟動查詢；沒有名額時依拒絕回覆節流政策回覆。
     *
     * @param R 不可為空的線路回應型別。
     * @param principalId 可信玩家身分。
     * @param onRejected 可以回覆拒絕訊息時執行的動作。
     * @param canStart 工作開始前的連線與 session 驗證。
     * @param query 查詢與必要內容裁切，不包含傳送回覆。
     * @param timeoutResponse 建立逾時回應。
     * @param failureResponse 將一般查詢例外轉為安全回應；不處理取消或致命錯誤。
     * @param isCurrentSession 回覆前再次驗證連線與 session。
     * @param send 主執行緒上的授權重驗與回覆動作；送出失敗不重送。
     * @return 已啟動的工作；未取得名額時為 null。
     */
    fun <R : Any> launch(
        principalId: Uuid,
        onRejected: (HistoryQueryAdmission.Admission) -> Unit = {},
        canStart: suspend () -> Boolean = { true },
        query: suspend () -> R,
        timeoutResponse: () -> R,
        failureResponse: (Exception) -> R,
        isCurrentSession: suspend () -> Boolean,
        send: suspend (R) -> Unit,
    ): Job? {
        val result = admission.acquire(principalId)
        if (result !is HistoryQueryAdmission.Admission.Accepted) {
            if (admission.shouldSendRejection(principalId)) onRejected(result)
            return null
        }
        return launchAccepted(principalId, result.token, canStart, query, timeoutResponse, failureResponse, isCurrentSession, send)
    }

    /**
     * 執行已取得名額的查詢，集中管理逾時、回覆與名額釋放。
     *
     * 查詢失敗可轉為錯誤回應，但連線檢查或傳送本身失敗不得再送第二份回應。
     * 完成回呼涵蓋工作開始前取消的情況；一次性旗標確保兩條清理路徑只釋放一次。
     *
     * @param R 不可為空的線路回應型別。
     * @param principalId 可信玩家身分。
     * @param token 已取得的准入權杖。
     * @param canStart 工作開始前的連線與 session 驗證。
     * @param query 查詢與必要內容裁切，不包含傳送回覆。
     * @param timeoutResponse 建立逾時回應。
     * @param failureResponse 將一般查詢例外轉為安全回應；不處理取消或致命錯誤。
     * @param isCurrentSession 回覆前再次驗證連線與 session。
     * @param send 主執行緒上的授權重驗與回覆動作；送出失敗不重送。
     * @return 已啟動的工作。
     */
    fun <R : Any> launchAccepted(
        principalId: Uuid,
        token: Uuid,
        canStart: suspend () -> Boolean,
        query: suspend () -> R,
        timeoutResponse: () -> R,
        failureResponse: (Exception) -> R,
        isCurrentSession: suspend () -> Boolean,
        send: suspend (R) -> Unit,
    ): Job {
        val released = AtomicBoolean(false)
        val release = {
            if (released.compareAndSet(false, true)) admission.release(principalId, token)
        }
        val job = scope.launch {
            try {
                if (!withContext(dispatchers.main) { canStart() }) return@launch
                val response = try {
                    withTimeoutOrNull(timeout) { query() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    failureResponse(failure)
                } ?: timeoutResponse()
                withContext(dispatchers.main) {
                    if (isCurrentSession()) send(response)
                }
            } finally {
                release()
            }
        }
        job.invokeOnCompletion { release() }
        return job
    }
}
