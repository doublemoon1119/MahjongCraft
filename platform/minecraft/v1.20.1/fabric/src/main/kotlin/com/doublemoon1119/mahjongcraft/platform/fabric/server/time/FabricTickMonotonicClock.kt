package com.doublemoon1119.mahjongcraft.platform.fabric.server.time

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlinx.coroutines.DisposableHandle
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.koin.core.annotation.Single
import java.util.PriorityQueue

/**
 * [MonotonicClock] 的 Fabric 實作，以 server tick 計數換算毫秒數，而不是真實系統時間。
 *
 * Minecraft 單機版按 ESC 開啟暫停選單時，`ServerTickEvents.END_SERVER_TICK` 不會觸發，這裡的計數
 * 因此自然停止前進——這正是玩家決策計時器需要的語意：暫停期間不該算進思考時間。專用伺服器沒有
 * 這種暫停機制，tick 本來就會持續以正常速度前進，行為跟真實時間等價，不受影響。
 *
 * 額外提供 [scheduleAfter]：以同一份 tick 計數排定延遲工作，供
 * [ServerThreadCoroutineDispatcher][com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerThreadCoroutineDispatcher]
 * 實作協程 `delay()` 使用，讓伺服器主執行緒上的延遲也一併具備暫停感知能力，不需要另外開一條跑真實
 * 時間的背景執行緒。
 */
@Single(binds = [MonotonicClock::class])
class FabricTickMonotonicClock : MonotonicClock {
    private var elapsedTicks: Long = 0
    private val pendingTasks = PriorityQueue<ScheduledTask>(compareBy { it.dueAtTick })
    private val pendingTasksLock = Any()

    /** 註冊 tick 事件；只增不減，跨對局／跨桌共用同一個遞增計數，符合 [MonotonicClock] 只能同一
     * session 內比較的約定。每個 tick 順便觸發到期的 [scheduleAfter] 工作。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register {
            elapsedTicks++
            runDueTasks()
        }
    }

    override fun nowMillis(): Long = elapsedTicks * MILLIS_PER_TICK

    /** 排定 [block] 在 [delayMillis] 之後（依 tick 無條件進位）於下個到期 tick 執行；回傳可取消的 handle。 */
    fun scheduleAfter(delayMillis: Long, block: Runnable): DisposableHandle {
        val delayTicks = (delayMillis + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK
        val task = ScheduledTask(elapsedTicks + delayTicks, block)
        synchronized(pendingTasksLock) { pendingTasks.add(task) }
        return DisposableHandle { synchronized(pendingTasksLock) { pendingTasks.remove(task) } }
    }

    /** 取出所有到期工作後才執行，避免持鎖期間執行任意程式碼。 */
    private fun runDueTasks() {
        val dueTasks = mutableListOf<ScheduledTask>()
        synchronized(pendingTasksLock) {
            while (pendingTasks.isNotEmpty() && pendingTasks.peek().dueAtTick <= elapsedTicks) {
                dueTasks += pendingTasks.poll()
            }
        }
        dueTasks.forEach { it.block.run() }
    }

    private class ScheduledTask(val dueAtTick: Long, val block: Runnable)

    private companion object {
        /** Minecraft 正常運行時每個 tick 對應的毫秒數（20 TPS）。 */
        const val MILLIS_PER_TICK: Long = 50L
    }
}
