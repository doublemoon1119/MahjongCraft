package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [AnimationQueueDriver] 的到期／推進判斷測試，涵蓋 bug 根因相關的持久化恢復情境。 */
class AnimationQueueDriverTest {
    /** 相同絕對時間的相鄰等待只保留一個。 */
    @Test
    fun `test append deduplicates adjacent waits with the same game time`() {
        val wait = AnimationStep.WaitUntil(100L)

        assertEquals(listOf(wait), AnimationQueueDriver.append(listOf(wait), listOf(wait)))
    }

    /** 相鄰等待的新目標較晚時，以較晚時間取代原本等待。 */
    @Test
    fun `test append extends adjacent wait to the later game time`() {
        assertEquals(
            listOf(AnimationStep.WaitUntil(120L)),
            AnimationQueueDriver.append(
                listOf(AnimationStep.WaitUntil(100L)),
                listOf(AnimationStep.WaitUntil(120L)),
            ),
        )
    }

    /** 相鄰等待的新目標較早時，不得縮短既有等待。 */
    @Test
    fun `test append never shortens an adjacent wait`() {
        assertEquals(
            listOf(AnimationStep.WaitUntil(120L)),
            AnimationQueueDriver.append(
                listOf(AnimationStep.WaitUntil(120L)),
                listOf(AnimationStep.WaitUntil(100L)),
            ),
        )
    }

    /** 中間隔著實際動作的等待屬於不同動畫階段，不可跨越動作合併。 */
    @Test
    fun `test append does not merge waits across another step`() {
        val teleport = AnimationStep.Teleport(1.0, 2.0, 3.0, 90.0f)
        val initial = listOf<AnimationStep<Nothing>>(AnimationStep.WaitUntil(100L), teleport)

        assertEquals(
            initial + AnimationStep.WaitUntil(100L),
            AnimationQueueDriver.append(initial, listOf(AnimationStep.WaitUntil(100L))),
        )
    }

    /** 佇列為空時什麼都不做，回傳空的剩餘佇列與 `null` 計時進度。 */
    @Test
    fun `test empty queue does nothing`() {
        val result = AnimationQueueDriver.tick<Nothing>(emptyList(), activeStepEndGameTime = null, currentGameTime = 100L)

        assertTrue(result.appliedInstantSteps.isEmpty())
        assertNull(result.startedPlayMotion)
        assertEquals(false, result.completedPlayMotion)
        assertTrue(result.remainingQueue.isEmpty())
        assertNull(result.activeStepEndGameTime)
    }

    /** 連續的瞬間 step 應該在同一次呼叫內全部套用，直到遇到下一個計時 step 為止。 */
    @Test
    fun `test consecutive instant steps apply together in one call`() {
        val teleport = AnimationStep.Teleport(1.0, 2.0, 3.0, 90.0f)
        val setInvisible = AnimationStep.SetInvisible(true)
        val waitUntil = AnimationStep.WaitUntil(5L)
        val queue = listOf<AnimationStep<Nothing>>(teleport, setInvisible, waitUntil)

        val result = AnimationQueueDriver.tick(queue, activeStepEndGameTime = null, currentGameTime = 0L)

        assertEquals(listOf(teleport, setInvisible), result.appliedInstantSteps)
        assertEquals(listOf(waitUntil), result.remainingQueue)
        assertNull(result.activeStepEndGameTime)
    }

    /** [AnimationStep.PlayMotion] 啟動當下就回報 `startedPlayMotion`，到期前不重複啟動。 */
    @Test
    fun `test play motion reports start once and completes when due`() {
        val motion = AnimationStep.PlayMotion(
            durationTicks = 4,
            arcHeight = 0.0,
            startOffsetX = 0.0,
            startOffsetY = 1.0,
            startOffsetZ = 0.0,
            startPoseRotationDegrees = 0.0f,
            endPoseRotationDegrees = 90.0f,
        )
        val queue = listOf<AnimationStep<Nothing>>(motion)

        val started = AnimationQueueDriver.tick(queue, activeStepEndGameTime = null, currentGameTime = 0L)
        assertEquals(motion, started.startedPlayMotion)
        assertEquals(false, started.completedPlayMotion)
        assertEquals(4L, started.activeStepEndGameTime)
        assertEquals(queue, started.remainingQueue)

        val midFlight = AnimationQueueDriver.tick(started.remainingQueue, started.activeStepEndGameTime, currentGameTime = 2L)
        assertNull(midFlight.startedPlayMotion)
        assertEquals(false, midFlight.completedPlayMotion)
        assertEquals(queue, midFlight.remainingQueue)

        val completed = AnimationQueueDriver.tick(midFlight.remainingQueue, midFlight.activeStepEndGameTime, currentGameTime = 4L)
        assertNull(completed.startedPlayMotion)
        assertTrue(completed.completedPlayMotion)
        assertTrue(completed.remainingQueue.isEmpty())
        assertNull(completed.activeStepEndGameTime)
    }

    /**
     * 這是修正「動畫播放中離開世界，牌卡在半空」bug 的核心場景：`WaitUntil` 攜帶的絕對到期時間存進
     * 世界存檔後不會變動，世界重新載入後第一個 tick 用當下（可能遠遠超過原本到期時間，例如伺服器落後
     * 進度一次補跑一大段 tick）的 `currentGameTime` 檢查，`WaitUntil` 應該正確判定為早已到期，緊接著
     * 在同一次呼叫內啟動下一個 `PlayMotion`，不會卡住、也不會因為誤判而提早跳過。
     */
    @Test
    fun `test wait until completion and next play motion start happen in the same tick`() {
        val waitUntil = AnimationStep.WaitUntil(105L)
        val motion = AnimationStep.PlayMotion(
            durationTicks = 3,
            arcHeight = 0.0,
            startOffsetX = 0.0,
            startOffsetY = 0.0,
            startOffsetZ = 0.0,
            startPoseRotationDegrees = 0.0f,
            endPoseRotationDegrees = 0.0f,
        )
        val queue = listOf<AnimationStep<Nothing>>(waitUntil, motion)

        // 模擬世界重新載入：WaitUntil 的到期時間 105 存檔時已經寫死，重新載入後第一個 tick 的
        // currentGameTime 遠遠超過 105。
        val result = AnimationQueueDriver.tick(queue, activeStepEndGameTime = null, currentGameTime = 1_000_000L)

        assertEquals(motion, result.startedPlayMotion)
        assertEquals(1_000_000L + 3, result.activeStepEndGameTime)
        assertEquals(listOf(motion), result.remainingQueue)
    }

    /** [AnimationStep.Custom] 攜帶的實體專屬資料原封不動出現在套用列表裡。 */
    @Test
    fun `test custom step is applied with its payload intact`() {
        val custom = AnimationStep.Custom("standing")
        val result = AnimationQueueDriver.tick(listOf(custom), activeStepEndGameTime = null, currentGameTime = 0L)

        assertEquals(listOf(custom), result.appliedInstantSteps)
        assertTrue(result.remainingQueue.isEmpty())
    }

    /**
     * [AnimationStep.WaitUntil] 停在原地直到 `currentGameTime` 到達攜帶的絕對 game time 為止，不需要
     * 額外的 `activeStepEndGameTime` 追蹤——這正是它適合用來讓多個不同 entity 收斂到同一個絕對時刻的
     * 原因：不管什麼時候第一次檢查，到期判斷永遠只看攜帶的固定值。
     */
    @Test
    fun `test wait until blocks until the absolute game time then completes`() {
        val waitUntil = AnimationStep.WaitUntil(110L)
        val queue = listOf<AnimationStep<Nothing>>(waitUntil)

        val early = AnimationQueueDriver.tick(queue, activeStepEndGameTime = null, currentGameTime = 100L)
        assertEquals(queue, early.remainingQueue)
        assertNull(early.activeStepEndGameTime)

        val stillEarly = AnimationQueueDriver.tick(queue, activeStepEndGameTime = null, currentGameTime = 109L)
        assertEquals(queue, stillEarly.remainingQueue)

        val due = AnimationQueueDriver.tick(queue, activeStepEndGameTime = null, currentGameTime = 110L)
        assertTrue(due.remainingQueue.isEmpty())
        assertNull(due.activeStepEndGameTime)
    }

    /**
     * 這是 [AnimationStep.WaitUntil] 存在的核心理由：多個彼此獨立的佇列，只要共用同一個算好的絕對
     * game time，不管各自佇列前面累積了多少不同時長的其他 step，到期的那一刻永遠一致——不需要呼叫端
     * 先精準算出「這個佇列自己已經花了多少 tick，還要再等多少才會跟其他佇列對齊」。
     */
    @Test
    fun `test wait until synchronizes independent queues regardless of prior elapsed ticks`() {
        val sharedTarget = 200L
        fun playMotion(durationTicks: Int) = AnimationStep.PlayMotion(
            durationTicks = durationTicks,
            arcHeight = 0.0,
            startOffsetX = 0.0,
            startOffsetY = 0.0,
            startOffsetZ = 0.0,
            startPoseRotationDegrees = 0.0f,
            endPoseRotationDegrees = 0.0f,
        )
        val shortMotion = playMotion(5)
        val longMotion = playMotion(50)
        val shortQueue = listOf<AnimationStep<Nothing>>(shortMotion, AnimationStep.WaitUntil(sharedTarget))
        val longQueue = listOf<AnimationStep<Nothing>>(longMotion, AnimationStep.WaitUntil(sharedTarget))

        val shortAfterMotion = AnimationQueueDriver.tick(shortQueue, activeStepEndGameTime = null, currentGameTime = 0L)
        val shortResult = AnimationQueueDriver.tick(
            shortAfterMotion.remainingQueue,
            shortAfterMotion.activeStepEndGameTime,
            currentGameTime = 199L,
        )
        assertEquals(listOf(AnimationStep.WaitUntil(sharedTarget)), shortResult.remainingQueue)

        val longAfterMotion = AnimationQueueDriver.tick(longQueue, activeStepEndGameTime = null, currentGameTime = 0L)
        val longResult = AnimationQueueDriver.tick(
            longAfterMotion.remainingQueue,
            longAfterMotion.activeStepEndGameTime,
            currentGameTime = 199L,
        )
        assertEquals(listOf(AnimationStep.WaitUntil(sharedTarget)), longResult.remainingQueue)

        val shortDone = AnimationQueueDriver.tick(shortResult.remainingQueue, shortResult.activeStepEndGameTime, currentGameTime = sharedTarget)
        val longDone = AnimationQueueDriver.tick(longResult.remainingQueue, longResult.activeStepEndGameTime, currentGameTime = sharedTarget)
        assertTrue(shortDone.remainingQueue.isEmpty())
        assertTrue(longDone.remainingQueue.isEmpty())
    }
}
