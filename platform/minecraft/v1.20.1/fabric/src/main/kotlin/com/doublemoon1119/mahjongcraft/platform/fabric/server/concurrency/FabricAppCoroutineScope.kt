package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricDecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/** 可隨 integrated/dedicated server session 重新建立工作的 Fabric 應用協程作用域。 */
@Single(binds = [AppCoroutineScope::class])
class FabricAppCoroutineScope(
    private val dispatchers: CoroutineDispatchers,
) : AppCoroutineScope {
    /** 兜底記錄未被個別呼叫端攔截的協程例外；[SupervisorJob] 下每個 child 各自獨立，這裡不會影響
     *  其他任務繼續執行，純粹是最後一道防線的 log。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

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
     * 不加 `@Synchronized`：掛起函式裡面呼叫 [withTimeoutOrNull]／[Job.join] 這種真正會掛起的呼叫，
     * 不能包在 synchronized 區塊裡（會在掛起期間持有 monitor，Kotlin 編譯器本來就禁止這樣寫）。
     * 這裡只讀取 [sessionContext] 一次取得目前的 [Job]，不修改欄位本身，跟 [coroutineContext] 這個
     * getter 一樣不需要跟 [startSession]／[cancel] 互斥。
     *
     * [newContext] 放進去的一定是 [SupervisorJob] 建立的 [CompletableJob]（`complete()` 定義在
     * [CompletableJob]，不在 [Job] 本身），從 [CoroutineContext] 用 `[Job]` 拿回來的靜態型別只有
     * [Job]，所以這裡轉型還原——執行期一定成立，拿不到才代表 context 本身有問題，直接放棄。
     */
    override suspend fun shutdown(timeoutMillis: Long) {
        val job = sessionContext[Job] as? CompletableJob ?: return
        job.complete()
        withTimeoutOrNull(timeoutMillis.milliseconds) { job.join() }
        job.cancel()
    }

    /**
     * 建立使用平台預設 dispatcher 且彼此錯誤隔離的新 session context。
     *
     * 掛上 [CoroutineExceptionHandler] 當作最後一道防線——個別呼叫端（例如[MahjongTableGameActionService]／[FabricDecisionTimerScheduler]）
     * 已經在自己的呼叫路徑上攔截並記錄例外；這裡只處理任何遺漏、沒有各自 try/catch 的
     * `scope.launch { ... }`，避免例外完全不留 log 就讓協程靜默死掉。
     */
    private fun newContext(): CoroutineContext = SupervisorJob() +
        dispatchers.default +
        CoroutineExceptionHandler { _, throwable -> logger.error("Unhandled coroutine exception", throwable) }
}
