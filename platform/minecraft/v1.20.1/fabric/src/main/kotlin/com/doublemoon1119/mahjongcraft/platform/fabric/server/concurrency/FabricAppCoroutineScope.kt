package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

/** 可隨 integrated/dedicated server session 重新建立工作的 Fabric 應用協程作用域。 */
@Single(binds = [AppCoroutineScope::class])
class FabricAppCoroutineScope(
    private val dispatchers: CoroutineDispatchers,
) : AppCoroutineScope {
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

    /** 建立使用平台預設 dispatcher 且彼此錯誤隔離的新 session context。 */
    private fun newContext(): CoroutineContext = SupervisorJob() + dispatchers.default
}
