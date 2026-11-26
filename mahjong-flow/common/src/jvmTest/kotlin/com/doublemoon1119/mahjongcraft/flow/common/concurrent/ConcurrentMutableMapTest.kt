package com.doublemoon1119.mahjongcraft.flow.common.concurrent

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 [concurrentMutableMap] 在多個執行緒同時寫入時不會遺漏。 */
class ConcurrentMutableMapTest {
    /** 多個執行緒同時寫入不同的鍵，全部寫入都留下。 */
    @Test
    fun `concurrent writes from many threads are all kept`() {
        val map = concurrentMutableMap<Int, Int>()
        val start = CountDownLatch(1)
        val writers = List(THREADS) { writer ->
            thread {
                start.await()
                repeat(KEYS_PER_THREAD) { index -> map[writer * KEYS_PER_THREAD + index] = writer }
            }
        }

        start.countDown()
        writers.forEach(Thread::join)

        assertEquals(THREADS * KEYS_PER_THREAD, map.size)
    }

    /** 測試規模。 */
    private companion object {
        /** 同時寫入的執行緒數。 */
        const val THREADS = 8

        /** 每個執行緒寫入的鍵數。 */
        const val KEYS_PER_THREAD = 2_000
    }
}
