package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * 依提交順序逐一執行桌子的權威清理，讓伺服器主執行緒的同步回呼不必等待清理完成。
 *
 * 權威清理需要權威狀態與快照倉庫的鎖；主執行緒的同步回呼以 runBlocking 等待這些鎖時，可能與排在前面、
 * 必須回到主執行緒才能繼續的協程互相等待。同步回呼因此只登記清理並立即返回，由這裡以主執行緒協程依序執行：
 * 等待鎖時只暫停協程、不佔住主執行緒，桌子位置索引等只允許主執行緒修改的資料也維持在主執行緒變更。
 * 工作在前一項完成後才開始；單一工作失敗只記錄錯誤，不影響後續工作。
 *
 * @property scope 目前 server session 的工作作用域；session 結束時尚未執行的工作隨之取消。
 * @property dispatchers 提供伺服器主執行緒排程器。
 */
@Single
class TableCleanupQueue(
    @Provided private val scope: AppCoroutineScope,
    @Provided private val dispatchers: CoroutineDispatchers,
) {
    /** 記錄單一清理工作的失敗。 */
    private val logger = mahjongCraftLogger(TableCleanupQueue::class)

    /** 最後提交的清理工作；下一項工作等它結束後才開始。 */
    private var lastJob: Job? = null

    /**
     * 排入一項清理工作，不等待它完成。
     *
     * 在主執行緒呼叫且前一項已完成時，工作會先執行到第一次暫停才返回；暫停後由主執行緒佇列繼續。
     *
     * @param description 失敗時寫入記錄的工作說明。
     * @param task 要在前一項工作結束後執行的清理。
     * @return 代表這項清理的工作。
     */
    fun submit(description: String, task: suspend () -> Unit): Job = synchronized(this) {
        val previous = lastJob
        scope.launch(dispatchers.main) {
            previous?.join()
            try {
                task()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                logger.error("Mahjong table cleanup failed: {}", description, failure)
            }
        }.also { lastJob = it }
    }
}
