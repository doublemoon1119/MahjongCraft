package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [AiDecisionExecutor.takeUnfinishedStrategyPeak] 在多條執行緒同時開始、結束策略工作時仍不漏算。 */
class AiDecisionExecutorPeakConcurrencyTest {
    /**
     * 每次讀取峰值前先看當下的工作數；讀到的峰值涵蓋那個時刻，因此不得小於它。讀取與重設若分開進行，期間開始的工作會被漏掉，
     * 峰值就可能小於當下的數量。
     */
    @Test
    fun `peak reads never miss calls running during the read`() = runBlocking {
        val workers = Executors.newFixedThreadPool(WORKER_THREADS).asCoroutineDispatcher()
        val readers = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val executor = AiDecisionExecutor(workers, capacity = CAPACITY)
            val done = AtomicBoolean(false)
            val violations = mutableListOf<String>()
            var reads = 0
            val reader = launch(readers) {
                while (!done.get()) {
                    val running = executor.unfinishedStrategyCalls
                    val peak = executor.takeUnfinishedStrategyPeak()
                    reads++
                    if (peak < running || peak > CAPACITY) violations += "peak=$peak running=$running"
                }
            }
            val playerId = Uuid.random()
            (1..CALLERS).map {
                async(Dispatchers.Default) {
                    repeat(DECISIONS_PER_CALLER) {
                        executor.decide(Uuid.random(), playerId, null, decide = {
                            yield()
                            "decided"
                        }, fallback = { "fallback" })
                    }
                }
            }.awaitAll()
            done.set(true)
            reader.join()

            assertEquals(emptyList(), violations)
            assertTrue(reads > 0)
            assertEquals(0, executor.unfinishedStrategyCalls)
            executor.takeUnfinishedStrategyPeak()
            assertEquals(0, executor.takeUnfinishedStrategyPeak())
        } finally {
            workers.close()
            readers.close()
        }
    }

    private companion object {
        /** 執行策略的執行緒數。 */
        const val WORKER_THREADS = 4

        /** 名額。 */
        const val CAPACITY = 6

        /** 同時發出決策的呼叫端數。 */
        const val CALLERS = 8

        /** 每個呼叫端發出的決策數。 */
        const val DECISIONS_PER_CALLER = 2_000
    }
}
