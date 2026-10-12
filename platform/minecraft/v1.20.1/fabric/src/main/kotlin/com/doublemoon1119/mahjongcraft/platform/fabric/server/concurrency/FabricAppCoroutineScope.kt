package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricDecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * 隨單人或專用伺服器啟動週期重新建立工作的 Fabric 應用協程作用域。
 *
 * @property dispatchers 提供預設工作 dispatcher 的平台 dispatcher 集合。
 */
@Single(binds = [AppCoroutineScope::class])
class FabricAppCoroutineScope(
    private val dispatchers: CoroutineDispatchers,
) : AppCoroutineScope {
    /**
     * 記錄未被個別呼叫端攔截的協程例外；各子工作由 [SupervisorJob] 隔離失敗。
     */
    private val logger = mahjongCraftLogger(FabricAppCoroutineScope::class)

    /** 目前伺服器啟動週期所用的協程情境。 */
    @Volatile
    private var sessionContext: CoroutineContext = newContext()

    /** 每次取用都回傳目前 server session 的 context，避免 service 永久持有已取消的 Job。 */
    override val coroutineContext: CoroutineContext
        get() = sessionContext

    /** 取消舊 session 的工作並建立可供新 server 使用的 context。 */
    @Synchronized
    fun startSession() {
        sessionContext.cancel()
        sessionContext = newContext()
    }

    /** 取消目前 session 中所有尚未完成的工作。 */
    @Synchronized
    override fun cancel() {
        sessionContext.cancel()
    }

    /**
     * 等待呼叫時的伺服器工作結束，最多等待 [timeout]，之後取消該週期尚未完成的工作。
     *
     * 僅讀取一次 [sessionContext]，不在等待期間持有同步鎖。呼叫端必須先停止啟動新工作；
     * 此方法不保證拒絕關閉期間新增的子工作。若情境中沒有 [CompletableJob]，直接返回。
     *
     * @param timeout 等待工作完成的最長時間。
     */
    override suspend fun shutdown(timeout: Duration) {
        val job = sessionContext[Job] as? CompletableJob ?: return
        job.complete()
        withTimeoutOrNull(timeout) { job.join() }
        job.cancel()
    }

    /**
     * 建立使用平台預設排程器、以 [SupervisorJob] 隔離子工作失敗的協程情境。
     *
     * [CoroutineExceptionHandler] 記錄未由呼叫端處理的例外，例如
     * [MahjongTableGameActionService] 或 [FabricDecisionTimerScheduler] 未自行攔截的失敗。
     *
     * @return 包含工作、預設排程器與例外處理器的新情境。
     */
    private fun newContext(): CoroutineContext = SupervisorJob() +
        dispatchers.default +
        CoroutineExceptionHandler { _, throwable -> logger.error("Unhandled coroutine exception", throwable) }
}
