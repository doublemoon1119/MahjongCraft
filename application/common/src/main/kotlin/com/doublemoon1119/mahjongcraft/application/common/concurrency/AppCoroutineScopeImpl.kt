package com.doublemoon1119.mahjongcraft.application.common.concurrency

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
    private val exceptionHandler: CoroutineExceptionHandler? = null
) : AppCoroutineScope {

    /**
     * 組合協程上下文。
     * 包含：SupervisorJob (隔離異常)、指定調度器、以及可選的異常處理器。
     */
    override val coroutineContext: CoroutineContext =
        SupervisorJob() + dispatchers.default + (exceptionHandler ?: EmptyCoroutineContext)

    /**
     * 取消整個協程上下文。
     */
    override fun cancel() {
        coroutineContext.cancel()
    }
}
