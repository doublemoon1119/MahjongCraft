package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricDecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * 隨單人或專用伺服器啟動週期重新建立工作的 Fabric 應用協程作用域，承接關服前必須完成的權威工作。
 *
 * 關服時以 [shutdown] 等待已開始的工作完成；可以捨棄的背景工作使用 [FabricSessionBackgroundScope]。
 * 未被呼叫端攔截的例外寫入記錄，例如 [MahjongTableGameActionService] 或 [FabricDecisionTimerScheduler]
 * 未自行攔截的失敗；各子工作由 [SupervisorJob] 隔離失敗。
 *
 * @param dispatchers 提供預設工作 dispatcher 的平台 dispatcher 集合。
 */
@Single(binds = [AppCoroutineScope::class])
class FabricAppCoroutineScope(
    dispatchers: CoroutineDispatchers,
) : AppCoroutineScope {
    /** 記錄未被個別呼叫端攔截的協程例外。 */
    private val logger = mahjongCraftLogger(FabricAppCoroutineScope::class)

    /** 隨 server session 重建的協程情境。 */
    private val session = SessionCoroutineContext(dispatcher = dispatchers.default) { throwable ->
        logger.error("Unhandled coroutine exception", throwable)
    }

    /** 每次取用都回傳目前 server session 的 context，避免 service 永久持有已取消的 Job。 */
    override val coroutineContext: CoroutineContext
        get() = session.current

    /** 取消舊 session 的工作並建立可供新 server 使用的 context。 */
    fun startSession() {
        session.startSession()
    }

    /** 取消目前 session 中所有尚未完成的工作。 */
    override fun cancel() {
        session.cancel()
    }

    /**
     * 等待呼叫時的伺服器工作結束，最多等待 [timeout]，之後取消該週期尚未完成的工作。
     *
     * 僅讀取一次目前情境，不在等待期間持有同步鎖。呼叫端必須先停止啟動新工作；
     * 此方法不保證拒絕關閉期間新增的子工作。若情境中沒有 [CompletableJob]，直接返回。
     *
     * @param timeout 等待工作完成的最長時間。
     */
    override suspend fun shutdown(timeout: Duration) {
        val job = session.current[Job] as? CompletableJob ?: return
        job.complete()
        withTimeoutOrNull(timeout) { job.join() }
        job.cancel()
    }
}
