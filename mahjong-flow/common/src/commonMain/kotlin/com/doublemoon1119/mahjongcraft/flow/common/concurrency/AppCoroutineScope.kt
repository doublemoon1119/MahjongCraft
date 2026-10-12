package com.doublemoon1119.mahjongcraft.flow.common.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 管理應用層共用協程作用域的生命週期。
 *
 * 提供共用的 [CoroutineScope]，並區分立即取消與限時等待現有工作完成兩種關閉方式。
 */
interface AppCoroutineScope : CoroutineScope {
    /**
     * 要求取消此作用域下的所有任務，不等待工作完成。
     *
     * 需要讓現有工作先完成時使用 [shutdown]；不配合取消的工作可能仍會繼續執行。
     */
    fun cancel()

    /**
     * 開始關閉作用域，最多等待 [timeout] 讓子工作完成，再要求取消剩餘工作。
     *
     * 呼叫端應先停止啟動新工作；此方法不保證拒絕或取消關閉期間新增的協程。
     * 逾時只會發出取消要求，不保證不配合取消的工作已經停止。
     *
     * @param timeout 最多等待現有工作完成的時間。
     */
    suspend fun shutdown(timeout: Duration = DEFAULT_SHUTDOWN_TIMEOUT)

    /** 共用的關閉預設值。 */
    companion object {
        /** [shutdown] 預設等待現有工作完成的時間。 */
        val DEFAULT_SHUTDOWN_TIMEOUT: Duration = 5.seconds
    }
}
