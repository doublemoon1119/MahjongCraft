package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 隨 server session 管理查詢設定發布 collector，避免重開 session 後沿用已取消的工作。
 *
 * @property scope 執行設定發布工作的作用域。
 * @property dispatchers 將設定通知切換至伺服器主執行緒的 dispatcher 集合。
 * @param updates 提供查詢間隔設定的更新來源。
 */
internal class HistoryQuerySettingsPublisher(
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    updates: Flow<Long>,
) {
    /** 設定來源只保留查詢間隔，避免將管理政策交給客戶端。 */
    private val intervalUpdates = updates.distinctUntilChanged()

    /** 目前 session 的設定發布工作。 */
    private var job: Job? = null

    /**
     * 啟動目前 session 的 collector；重複啟動會先停止舊 collector。
     *
     * @param publish 發布單一查詢間隔的動作。
     */
    fun start(publish: suspend (Long) -> Unit) {
        stop()
        job = scope.launch {
            intervalUpdates.collect { interval ->
                withContext(dispatchers.main) { publish(interval) }
            }
        }
    }

    /** 停止目前 session 的 collector，不影響設定來源或下一個 session。 */
    fun stop() {
        job?.cancel()
        job = null
    }
}
