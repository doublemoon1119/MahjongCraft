package com.doublemoon1119.mahjongcraft.flow.common.concurrency

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * [AppCoroutineScope] 的具體實作。
 *
 * 採用 [SupervisorJob] 以實現異常隔離，並允許透過傳入 [CoroutineExceptionHandler] 自定義未捕獲異常的處理策略。
 *
 * @property dispatchers 提供協程執行所需的調度器。
 * @property exceptionHandler 可選的異常處理器。若為 null，則異常將傳播至協程預設的處理機制。
 */
class AppCoroutineScopeImpl(
    private val dispatchers: CoroutineDispatchers,
    private val exceptionHandler: CoroutineExceptionHandler? = null,
) : AppCoroutineScope {

    /**
     * [SupervisorJob] 本身回傳的是 [CompletableJob]（有 `complete()`），但放進 [CoroutineContext] 後
     * 用 `coroutineContext[Job]` 拿回來的靜態型別只有 [kotlinx.coroutines.Job]——[shutdown] 需要呼叫
     * `complete()`，所以额外保留這個型別正確的參照，不從 context 裡再拿一次。
     */
    private val job: CompletableJob = SupervisorJob()

    /**
     * 組合協程上下文。
     * 包含：SupervisorJob (隔離異常)、指定調度器、以及可選的異常處理器。
     */
    override val coroutineContext: CoroutineContext =
        job + dispatchers.default + (exceptionHandler ?: EmptyCoroutineContext)

    /**
     * 取消整個協程上下文。
     */
    override fun cancel() {
        coroutineContext.cancel()
    }

    override suspend fun shutdown(timeoutMillis: Long) {
        job.complete()
        withTimeoutOrNull(timeoutMillis.milliseconds) { job.join() }
        job.cancel()
    }
}
