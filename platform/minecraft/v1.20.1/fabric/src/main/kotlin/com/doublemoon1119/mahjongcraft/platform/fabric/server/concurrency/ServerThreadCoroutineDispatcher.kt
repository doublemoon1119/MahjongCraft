package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.coroutines.CoroutineContext

/**
 * 把協程排程丟回 Minecraft 伺服器主執行緒（tick thread）——透過
 * [net.minecraft.server.MinecraftServer.execute] 把工作丟進伺服器自己的任務佇列，在下個 tick 開始
 * 前執行，不是另開執行緒。伺服器尚未啟動（[FabricServerHolder] 還沒被設值）時退回
 * [Dispatchers.Default]，避免任務憑空消失；正常運作下走不到這個分支——真的走到代表伺服器還沒啟動
 * 就有協程想切到主執行緒，屬於呼叫端的邏輯錯誤。
 *
 * 額外實作 [Delay]：不實作的話，這個 dispatcher 上的 `delay()` 會退回 kotlinx.coroutines 內建、
 * 綁定真實系統時間的預設延遲機制，跟 [FabricTickMonotonicClock] 解決的問題是同一類——Minecraft
 * 單機版暫停時背景執行緒仍然照常計時，恢復後會立刻觸發「早就到期」的延遲工作。改委派給
 * [tickClock] 的 tick 計數排程，暫停時自然停止前進，不需要另開一條背景執行緒。
 */
@OptIn(InternalCoroutinesApi::class)
class ServerThreadCoroutineDispatcher(
    private val serverHolder: FabricServerHolder,
    private val tickClock: FabricTickMonotonicClock,
) : CoroutineDispatcher(),
    Delay {
    /**
     * 已經在伺服器主執行緒上就不需要再 dispatch——`MinecraftServer.execute()` 內部本來就會做這個
     * 判斷（已驗證：`ThreadExecutor.execute()` 會呼叫 `shouldExecuteAsync()`／`isOnThread()`，在
     * 執行緒上就直接同步執行，不會排隊），這裡覆寫純粹是省一層呼叫開銷，不影響行為。伺服器尚未啟動
     * 時保守回傳 `true`，交給 [dispatch] 走 [Dispatchers.Default] 分支。
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
