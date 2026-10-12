package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.coroutines.CoroutineContext

/**
 * 透過 [net.minecraft.server.MinecraftServer.execute] 將協程工作交給 Minecraft 伺服器主執行緒。
 * 已在主執行緒時可直接執行，否則進入伺服器任務佇列，不保證恰好在下一個 tick 邊界執行。
 * 沒有目前伺服器時使用 [Dispatchers.Default]，此時不提供伺服器主執行緒保證。
 *
 * [Delay] 委派給 [tickClock]，依遊戲 tick 計算延遲，而非真實時間；單人世界暫停時延遲也停止前進。
 *
 * @property serverHolder 查詢目前 server session。
 * @property tickClock 依遊戲 tick 排程延遲工作的時鐘。
 */
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class ServerThreadCoroutineDispatcher(
    private val serverHolder: FabricServerHolder,
    private val tickClock: FabricTickMonotonicClock,
) : CoroutineDispatcher(),
    Delay {
    /**
     * 已在伺服器主執行緒時無須重新排程；沒有目前伺服器時交由 [dispatch] 使用預設排程器。
     *
     * @param context 要執行工作的協程情境。
     * @return 不在目前伺服器主執行緒，或沒有目前伺服器時為 true。
     */
    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        val server = serverHolder.current() ?: return true
        return !server.isOnThread
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val server = serverHolder.current()
        if (server != null) {
            server.execute(block)
        } else {
            Dispatchers.Default.dispatch(context, block)
        }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val handle = tickClock.scheduleAfter(timeMillis) {
            dispatch(continuation.context) {
                with(continuation) { resumeUndispatched(Unit) }
            }
        }
        continuation.invokeOnCancellation { handle.dispose() }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle = tickClock.scheduleAfter(timeMillis) { dispatch(context, block) }
}
