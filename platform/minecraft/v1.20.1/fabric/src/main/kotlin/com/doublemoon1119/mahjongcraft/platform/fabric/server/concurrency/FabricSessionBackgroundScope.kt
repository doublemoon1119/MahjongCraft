package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

/**
 * 目前 server session 中可以隨時捨棄的背景工作，例如狀態訂閱、呈現動畫與斷線倒數。
 *
 * 關服時先取消這裡的工作，不等待它們完成；它們不會自行結束或依賴遊戲 tick 前進，等待只會拖慢關服。
 * 需要在關服前完成的權威工作使用 [FabricAppCoroutineScope]。各子工作由 [SupervisorJob] 隔離失敗。
 *
 * @param dispatchers 提供預設工作 dispatcher 的平台 dispatcher 集合。
 */
@Single(binds = [FabricSessionBackgroundScope::class])
class FabricSessionBackgroundScope(
    dispatchers: CoroutineDispatchers,
) : CoroutineScope {
    /** 記錄未被個別呼叫端攔截的協程例外。 */
    private val logger = mahjongCraftLogger(FabricSessionBackgroundScope::class)

    /** 隨 server session 重建的協程情境。 */
    private val session = SessionCoroutineContext(dispatcher = dispatchers.default) { throwable ->
        logger.error("Unhandled background coroutine exception", throwable)
    }

    /** 每次取用都回傳目前 server session 的 context，避免 service 永久持有已取消的 Job。 */
    override val coroutineContext: CoroutineContext
        get() = session.current

    /** 取消舊 session 的背景工作並建立可供新 server 使用的 context。 */
    fun startSession() {
        session.startSession()
    }

    /** 取消目前 session 的所有背景工作，不等待它們結束。 */
    fun cancel() {
        session.cancel()
    }
}
