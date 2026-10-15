package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * 交給伺服器主執行緒執行的協程工作。
 *
 * 平常由伺服器任務佇列觸發 [drain]，執行時機與直接排入伺服器任務佇列相同。關服時 Minecraft 不再執行最近排入的
 * 伺服器任務，關服流程改在主執行緒呼叫 [drainUntil] 直接處理這裡的工作，讓在途協程能繼續並釋放持有的鎖。
 */
class MainThreadTaskQueue : MainThreadDrain {
    /** 尚未執行的工作，依排入順序執行。 */
    private val tasks = ConcurrentLinkedQueue<Runnable>()

    /** 正在 [drainUntil] 中等待工作的執行緒；有新工作時喚醒它。 */
    @Volatile
    private var drainingThread: Thread? = null

    /**
     * 排入一項工作，並喚醒正在 [drainUntil] 中等待的執行緒。
     *
     * @param task 要在主執行緒執行的工作。
     */
    fun enqueue(task: Runnable) {
        tasks.add(task)
        drainingThread?.let(LockSupport::unpark)
    }

    /**
     * 執行呼叫當下已排入的工作；執行期間新排入的工作留給之後的處理，避免持續排入的工作佔住主執行緒。
     *
     * @return 執行的工作數。
     */
    fun drain(): Int {
        var remaining = tasks.size
        var executed = 0
        while (remaining-- > 0) {
            val task = tasks.poll() ?: break
            task.run()
            executed++
        }
        return executed
    }

    override fun drainUntil(isDone: () -> Boolean, timeout: Duration): Boolean {
        val deadline = TimeSource.Monotonic.markNow() + timeout
        drainingThread = Thread.currentThread()
        try {
            while (true) {
                drain()
                if (isDone()) return true
                val remaining = -deadline.elapsedNow()
                if (!remaining.isPositive()) return false
                if (tasks.isEmpty()) LockSupport.parkNanos(minOf(remaining, MAX_IDLE_WAIT).inWholeNanoseconds)
            }
        } finally {
            drainingThread = null
        }
    }

    /** [MAX_IDLE_WAIT] 所在的伴生物件。 */
    private companion object {
        /** 沒有工作時單次等待的上限；完成條件可能在沒有新工作時改變，需要定期重新檢查。 */
        val MAX_IDLE_WAIT: Duration = 10.milliseconds
    }
}
