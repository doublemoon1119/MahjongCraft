package doublemoon.mahjongcraft.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stands in for the Minecraft server thread: one named thread with a task queue.
 */
class QueueingDispatcherTest {
    @Volatile
    private var ownerThread: Thread? = null
    private val owner = Executors.newSingleThreadExecutor { Thread(it, "test-server-thread").also { t -> ownerThread = t } }
    private val dispatcher = QueueingDispatcher { owner.execute(it) }

    @AfterTest
    fun shutdown() {
        owner.shutdownNow()
        owner.awaitTermination(5, TimeUnit.SECONDS)
    }

    @Test
    fun `every resumption of a game coroutine runs on the owner thread`() = runBlocking {
        val seenThreads = Collections.synchronizedList(mutableListOf<Thread>())
        val record = { seenThreads += Thread.currentThread() }

        val job = CoroutineScope(dispatcher).launch {
            record()
            delay(20) // kotlinx delay: timer fires on another thread
            record()
            withContext(Dispatchers.Default) { delay(5) } // work elsewhere, then come back
            record()
            // Resumed from a foreign thread, like ServerScheduler resuming delayOnServer.
            suspendCancellableCoroutine { cont -> Thread { cont.resume(Unit) }.start() }
            record()
        }
        withTimeout(5_000) { job.join() }

        assertEquals(4, seenThreads.size)
        assertTrue(seenThreads.all { it === ownerThread }, "ran on: $seenThreads")
    }

    @Test
    fun `launching from the owner thread queues the coroutine instead of running it inline`() {
        val order = CompletableFuture<List<String>>()
        owner.execute {
            val steps = Collections.synchronizedList(mutableListOf<String>())
            CoroutineScope(dispatcher).launch {
                steps += "coroutine body"
                order.complete(steps.toList())
            }
            steps += "caller continued"
        }
        assertEquals(listOf("caller continued", "coroutine body"), order.get(5, TimeUnit.SECONDS))
    }
}
