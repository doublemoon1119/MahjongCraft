package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

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
     * 建立使用平台預設 dispatcher 且彼此錯誤隔離的新 session context。
     *
     * 掛上 [CoroutineExceptionHandler] 當作最後一道防線——個別呼叫端（例如
     * [com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService]／
     * [com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricDecisionTimerScheduler]）
     * 已經在自己的呼叫路徑上攔截並記錄例外；這裡只處理任何遺漏、沒有各自 try/catch 的
     * `scope.launch { ... }`，避免例外完全不留 log 就讓協程靜默死掉。
     */
    private fun newContext(): CoroutineContext = SupervisorJob() +
        dispatchers.default +
        CoroutineExceptionHandler { _, throwable -> logger.error("Unhandled coroutine exception", throwable) }
}
