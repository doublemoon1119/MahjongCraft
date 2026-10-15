package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 在伺服器主執行緒依序執行關服收尾，等待期間持續處理排給主執行緒的協程工作。
 *
 * 收尾以主執行緒協程執行：暫停等待時不佔住主執行緒，在途協程與被取消的協程都能回到主執行緒繼續並釋放持有的鎖。
 * 單一步驟失敗只記錄錯誤，其餘步驟照常執行。超過時限時記錄停在哪一步並取消剩下的收尾，讓關服繼續；
 * 已提交的權威狀態在每次交易時已交給世界保存容器，不因此遺失。
 *
 * @property mainDispatcher 伺服器主執行緒排程器。
 * @property drain 在主執行緒直接處理排入工作的入口。
 */
class FabricServerStopRunner(
    private val mainDispatcher: CoroutineDispatcher,
    private val drain: MainThreadDrain,
) {
    /** 記錄逾時與收尾失敗。 */
    private val logger = mahjongCraftLogger(FabricServerStopRunner::class)

    /**
     * 關服收尾的一個步驟。
     *
     * @property name 失敗或逾時時寫入記錄的步驟名稱。
     * @property action 步驟內容。
     */
    class StopStep(
        val name: String,
        val action: suspend () -> Unit,
    )

    /**
     * 依序執行關服收尾並等待它結束；只能在伺服器主執行緒呼叫。
     *
     * @param steps 依序執行的收尾步驟。
     * @param timeout 等待全部步驟的上限。
     * @return 失敗或未在時限內完成的步驟名稱，依步驟順序排列；全部完成時為空清單。
     */
    fun run(
        steps: List<StopStep>,
        timeout: Duration = STOP_TIMEOUT,
    ): List<String> {
        val unfinished = mutableListOf<String>()
        var nextIndex = 0
        val job = CoroutineScope(SupervisorJob()).launch(mainDispatcher) {
            while (nextIndex < steps.size) {
                val step = steps[nextIndex]
                try {
                    step.action()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    logger.error("Server stop cleanup step '{}' failed", step.name, exception)
                    unfinished += step.name
                }
                nextIndex++
            }
        }
        if (!drain.drainUntil(isDone = { job.isCompleted }, timeout = timeout)) {
            val remaining = steps.drop(nextIndex).map(StopStep::name)
            logger.error(
                "Server stop cleanup did not finish within {} at step '{}'; skipped {} and continuing shutdown",
                timeout,
                remaining.firstOrNull(),
                remaining.drop(1),
            )
            job.cancel()
            drain.drainUntil(isDone = { job.isCompleted }, timeout = CANCELLATION_GRACE)
            unfinished += remaining
        }
        return unfinished
    }

    /** 關服收尾時限所在的伴生物件。 */
    companion object {
        /** 等待收尾的預設上限。 */
        val STOP_TIMEOUT: Duration = 10.seconds

        /** 逾時取消後，讓被取消的收尾回到主執行緒結束的時間。 */
        val CANCELLATION_GRACE: Duration = 1.seconds
    }
}
