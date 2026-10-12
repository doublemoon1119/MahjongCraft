package com.doublemoon1119.mahjongcraft.flow.common.concurrency

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

/**
 * 使用 [SupervisorJob] 隔離子工作失敗的共用作用域。
 *
 * @param dispatchers 提供作用域預設使用的調度器。
 * @param exceptionHandler 未捕獲例外的處理器；null 時使用協程預設的例外處理機制。
 */
class AppCoroutineScopeImpl(
    dispatchers: CoroutineDispatchers,
    exceptionHandler: CoroutineExceptionHandler? = null,
) : AppCoroutineScope {

    /** 管理子工作，供 [shutdown] 等待完成或要求取消的根工作。 */
    private val job: CompletableJob = SupervisorJob()

    /**
     * 組合根工作、預設調度器與可選的例外處理器。
     */
    override val coroutineContext: CoroutineContext =
        job + dispatchers.default + (exceptionHandler ?: EmptyCoroutineContext)

    /**
     * 取消整個協程上下文。
     */
    override fun cancel() {
        coroutineContext.cancel()
    }

    override suspend fun shutdown(timeout: Duration) {
        job.complete()
        withTimeoutOrNull(timeout) { job.join() }
        job.cancel()
    }
}
