package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** [SegmentTimingDispatcher] 只計算協程實際執行的片段，不計等待期間。 */
class SegmentTimingDispatcherTest {
    /** 協程的兩個執行片段各自計時，掛起等待結果的期間不計入。 */
    @Test
    fun `only executed segments are timed`() {
        val clock = TestTimeSource()
        val segments = mutableListOf<Duration>()
        val dispatcher = SegmentTimingDispatcher(ImmediateDispatcher, clock)
        val result = CompletableDeferred<Unit>()

        CoroutineScope(Job() + dispatcher).launch(StepTiming { segments += it }) {
            clock += 3.milliseconds
            result.await()
            clock += 2.milliseconds
        }
        clock += 500.milliseconds
        result.complete(Unit)

        assertEquals(listOf(3.milliseconds, 2.milliseconds), segments)
    }

    /** 沒有 [StepTiming] 的協程不計時。 */
    @Test
    fun `coroutines without timing are not timed`() {
        val segments = mutableListOf<Duration>()
        var ran = false

        CoroutineScope(Job() + SegmentTimingDispatcher(ImmediateDispatcher)).launch { ran = true }

        assertEquals(true, ran)
        assertEquals(emptyList(), segments)
    }

    /** 收到工作就立即執行的調度器，比照伺服器主執行緒在自己執行緒上收到工作時的行為。 */
    private object ImmediateDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }
}
