package com.doublemoon1119.mahjongcraft.flow.common.concurrency

import kotlinx.coroutines.CoroutineScope

/**
 * 應用層全局協程作用域管理器。
 *
 * 此介面負責提供一個受控的 [CoroutineScope]，用於執行那些不與特定生命週期組件綁定的長期異步任務。
 * 遵循 Clean Architecture 原則，將協程作用域的生命週期管理抽象化。
 */
interface AppCoroutineScope : CoroutineScope {
    /**
     * 立即取消此作用域下的所有任務並釋放資源，不等待任何工作完成。
     *
     * 呼叫端需要的通常不是這個立即中斷版本，而是 [shutdown]——先停止接受新工作、讓已經在執行的工作
     * 有機會自然完成，再取消。這個方法保留給真的需要立即中斷（例如切換 server session，見
     * `FabricAppCoroutineScope.startSession`）的情境使用。
     */
    fun cancel()

    /**
     * 優雅關閉此作用域：先停止接受新工作（呼叫後任何試圖用這個 scope 啟動的協程都會被立即取消，
     * 不會真的執行），接著最多等待 [timeoutMillis] 毫秒讓目前已經在執行的工作自然完成，逾時仍未完成
     * 的工作最後才強制取消。
     *
     * 這是 [cancel] 的安全版本：伺服器關閉時如果直接呼叫 [cancel]，任何還沒真的執行到、卡在等鎖
     * （例如寫入權威狀態的 mutex）的協程會被攔腰砍斷——那次原本該發生的狀態變更完全不會發生，也不會
     * 留下任何訊號，是曾經真的踩過的問題，不是假設。呼叫端（例如伺服器 `SERVER_STOPPING` 處理）
     * 應該用這個方法取代 [cancel]，確保「停止接受新命令」跟「讓已經接受的命令跑完」之間有清楚的
     * 先後順序，而不是兩者混在同一個粗暴的取消動作裡。
     *
     * @param timeoutMillis 最多等待現有工作完成的毫秒數；逾時仍未完成的工作會被強制取消，不會無限期
     *   卡住呼叫端。
     */
    suspend fun shutdown(timeoutMillis: Long = DEFAULT_SHUTDOWN_TIMEOUT_MILLIS)

    companion object {
        /** [shutdown] 預設等待現有工作完成的毫秒數。 */
        const val DEFAULT_SHUTDOWN_TIMEOUT_MILLIS: Long = 5_000L
    }
}
