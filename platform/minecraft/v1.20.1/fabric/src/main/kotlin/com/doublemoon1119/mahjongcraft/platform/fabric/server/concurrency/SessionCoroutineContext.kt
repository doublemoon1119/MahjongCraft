package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * 隨 server session 重建的協程情境，供各 session 作用域共用。
 *
 * 每個情境以 [SupervisorJob] 隔離子工作失敗，未被呼叫端攔截的例外交給 [onUnhandled]。
 *
 * @property dispatcher 子工作預設使用的排程器。
 * @property onUnhandled 接收未被呼叫端攔截的協程例外。
 */
internal class SessionCoroutineContext(
    private val dispatcher: CoroutineDispatcher,
    private val onUnhandled: (Throwable) -> Unit,
) {
    /** 目前伺服器啟動週期所用的協程情境。 */
    @Volatile
    private var context: CoroutineContext = newContext()

    /** 目前 server session 的情境；每次取用都讀取最新值，避免永久持有已取消的 Job。 */
    val current: CoroutineContext
        get() = context

    /** 取消舊 session 的工作並建立可供新 server 使用的情境。 */
    @Synchronized
    fun startSession() {
        context.cancel()
        context = newContext()
    }

    /** 取消目前 session 中所有尚未完成的工作。 */
    @Synchronized
    fun cancel() {
        context.cancel()
    }

    /**
     * 建立使用 [dispatcher]、以 [SupervisorJob] 隔離子工作失敗的協程情境。
     *
     * @return 包含工作、排程器與例外處理器的新情境。
     */
    private fun newContext(): CoroutineContext = SupervisorJob() +
        dispatcher +
        CoroutineExceptionHandler { _, throwable -> onUnhandled(throwable) }
}
