package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** 驗證歷史查詢共用執行骨架的准入、取消、逾時與回覆生命週期。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryQueryExecutionTest {
    /** 連線檢查與送出回覆只在主執行緒，查詢內容留在背景執行。 */
    @Test
    fun `connection predicates and delivery use main while queries use background`() = runTest {
        val background = StandardTestDispatcher(testScheduler, "background")
        val mainDispatcher = StandardTestDispatcher(testScheduler, "main")
        val dispatchers = object : CoroutineDispatchers {
            override val default = background

            override val io = background

            override val main = mainDispatcher

            override val aiDecision = background

            override val aiDecisionParallelism = 1
        }
        val scope = createTestAppCoroutineScope(dispatchers)
        var sent = false
        HistoryQueryExecution(scope, dispatchers, admission(), 1.seconds).launch(
            principalId = Uuid.random(),
            canStart = {
                assertSame(mainDispatcher, currentCoroutineContext()[ContinuationInterceptor])
                true
            },
            query = {
                assertSame(background, currentCoroutineContext()[ContinuationInterceptor])
                "ok"
            },
            timeoutResponse = { "timeout" },
            failureResponse = { throw it },
            isCurrentSession = {
                assertSame(mainDispatcher, currentCoroutineContext()[ContinuationInterceptor])
                true
            },
            send = {
                assertSame(mainDispatcher, currentCoroutineContext()[ContinuationInterceptor])
                sent = true
            },
        )
        advanceUntilIdle()
        assertTrue(sent)
    }

    /** 回覆傳送失敗只向作用域回報一次，不再轉成另一份錯誤回覆。 */
    @Test
    fun `delivery failure is not retried as an error response`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val errors = mutableListOf<Throwable>()
        val scope = createTestAppCoroutineScope(TestDispatchers(dispatcher), CoroutineExceptionHandler { _, failure -> errors += failure })
        val admission = admission()
        var sends = 0
        var mappedFailures = 0
        val expected = IllegalStateException("Delivery failed")
        val player = Uuid.random()
        HistoryQueryExecution(scope, TestDispatchers(dispatcher), admission, 1.seconds).launch(
            principalId = player,
            query = { "ok" },
            timeoutResponse = { "timeout" },
            failureResponse = {
                mappedFailures++
                "failed"
            },
            isCurrentSession = { true },
            send = {
                sends++
                throw expected
            },
        )
        advanceUntilIdle()
        assertEquals(1, sends)
        assertEquals(0, mappedFailures)
        assertEquals(1, errors.size)
        assertTrue(generateSequence(errors.single()) { it.cause }.any { it === expected })
        assertIs<HistoryQueryAdmission.Admission.Accepted>(admission.acquire(player))
    }

    /** 工作開始前取消仍會釋放准入名額，且不執行查詢。 */
    @Test
    fun `cancellation before start releases admission without running query`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = createTestAppCoroutineScope(TestDispatchers(dispatcher))
        val admission = admission()
        val execution = HistoryQueryExecution(scope, TestDispatchers(dispatcher), admission, 1.seconds)
        val player = Uuid.random()
        var queried = false
        val job = requireNotNull(
            value = execution.launch(
                principalId = player,
                query = {
                    queried = true
                    "ok"
                },
                timeoutResponse = { "timeout" },
                failureResponse = { "failed" },
                isCurrentSession = { true },
                send = {},
            ),
        )

        job.cancel()
        runCurrent()

        assertFalse(queried)
        assertIs<HistoryQueryAdmission.Admission.Accepted>(admission.acquire(player))
    }

    /** 一般工作例外轉為失敗回覆，且不把例外重新拋給作用域。 */
    @Test
    fun `query exception sends failure response and releases admission`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = createTestAppCoroutineScope(TestDispatchers(dispatcher))
        val admission = admission()
        val execution = HistoryQueryExecution(scope, TestDispatchers(dispatcher), admission, 1.seconds)
        val player = Uuid.random()
        val responses = mutableListOf<String>()

        execution.launch(
            principalId = player,
            query = { error("database failure") },
            timeoutResponse = { "timeout" },
            failureResponse = { "failed" },
            isCurrentSession = { true },
            send = { responses += it },
        )
        advanceUntilIdle()

        assertEquals(listOf("failed"), responses)
        assertIs<HistoryQueryAdmission.Admission.Accepted>(admission.acquire(player))
    }

    /** 背景工作超過 deadline 時只送出逾時回覆。 */
    @Test
    fun `query timeout sends timeout response`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = createTestAppCoroutineScope(TestDispatchers(dispatcher))
        val admission = admission()
        val execution = HistoryQueryExecution(scope, TestDispatchers(dispatcher), admission, 100.milliseconds)
        val responses = mutableListOf<String>()

        execution.launch(
            principalId = Uuid.random(),
            query = {
                delay(1.seconds)
                "late"
            },
            timeoutResponse = { "timeout" },
            failureResponse = { "failed" },
            isCurrentSession = { true },
            send = { responses += it },
        )
        advanceUntilIdle()

        assertEquals(listOf("timeout"), responses)
    }

    /** 權限或 session 在回覆前失效時不得傳送背景查詢結果。 */
    @Test
    fun `invalid permission or session suppresses response`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = createTestAppCoroutineScope(TestDispatchers(dispatcher))
        val admission = admission()
        val execution = HistoryQueryExecution(scope, TestDispatchers(dispatcher), admission, 1.seconds)
        val responses = mutableListOf<String>()

        execution.launch(
            principalId = Uuid.random(),
            query = { "secret" },
            timeoutResponse = { "timeout" },
            failureResponse = { "failed" },
            isCurrentSession = { false },
            send = { responses += it },
        )
        advanceUntilIdle()

        assertTrue(responses.isEmpty())
    }

    /** 名額上限與拒絕回覆節流同時生效，取消第一份工作後才可接受下一份。 */
    @Test
    fun `rejection is rate limited while outstanding admission is released once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = createTestAppCoroutineScope(TestDispatchers(dispatcher))
        var now = 0L
        val admission = HistoryQueryAdmission(
            limits = { HistoryQueryAdmissionLimits(minimumInterval = Duration.ZERO, maximumOutstanding = 1, rejectionResponseInterval = 1.seconds) },
            now = { now },
        )
        val execution = HistoryQueryExecution(scope, TestDispatchers(dispatcher), admission, 1.seconds)
        val firstPlayer = Uuid.random()
        val secondPlayer = Uuid.random()
        val rejections = mutableListOf<HistoryQueryAdmission.Admission>()
        val first = requireNotNull(
            value = execution.launch(
                principalId = firstPlayer,
                query = { awaitCancellation() },
                timeoutResponse = { Unit },
                failureResponse = { Unit },
                isCurrentSession = { true },
                send = {},
            ),
        )
        runCurrent()

        assertEquals(
            expected = null,
            actual = execution.launch(
                principalId = secondPlayer,
                onRejected = { rejections += it },
                query = { Unit },
                timeoutResponse = { Unit },
                failureResponse = { Unit },
                isCurrentSession = { true },
                send = {},
            ),
        )
        assertEquals(
            expected = null,
            actual = execution.launch(
                principalId = secondPlayer,
                onRejected = { rejections += it },
                query = { Unit },
                timeoutResponse = { Unit },
                failureResponse = { Unit },
                isCurrentSession = { true },
                send = {},
            ),
        )
        assertEquals(1, rejections.size)

        first.cancel(CancellationException("test cancellation"))
        advanceUntilIdle()
        now = 2.seconds.inWholeNanoseconds
        assertIs<HistoryQueryAdmission.Admission.Accepted>(admission.acquire(secondPlayer))
    }

    /** 完成的舊權杖不能解除同一玩家後續取得的工作名額。 */
    @Test
    fun `released token cannot release the next accepted request`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = createTestAppCoroutineScope(TestDispatchers(dispatcher))
        val admission = HistoryQueryAdmission(
            limits = { HistoryQueryAdmissionLimits(minimumInterval = Duration.ZERO, maximumOutstanding = 1) },
            now = { 0L },
        )
        val execution = HistoryQueryExecution(scope, TestDispatchers(dispatcher), admission, 1.seconds)
        val player = Uuid.random()
        val accepted = assertIs<HistoryQueryAdmission.Admission.Accepted>(admission.acquire(player))

        execution.launchAccepted(
            principalId = player,
            token = accepted.token,
            canStart = { true },
            query = { Unit },
            timeoutResponse = { Unit },
            failureResponse = { Unit },
            isCurrentSession = { true },
            send = {},
        )
        advanceUntilIdle()

        val next = assertIs<HistoryQueryAdmission.Admission.Accepted>(admission.acquire(player))
        admission.release(player, accepted.token)
        admission.release(player, accepted.token)
        assertIs<HistoryQueryAdmission.Admission.Busy>(admission.acquire(player))
        assertIs<HistoryQueryAdmission.Admission.Busy>(admission.acquire(Uuid.random()))
        admission.release(player, next.token)
        assertIs<HistoryQueryAdmission.Admission.Accepted>(admission.acquire(Uuid.random()))
    }

    /**
     * 建立不受預設頻率限制影響的測試准入。
     *
     * @return 固定時間且不限制最短請求間隔的准入管理器。
     */
    private fun admission(): HistoryQueryAdmission = HistoryQueryAdmission(
        limits = { HistoryQueryAdmissionLimits(minimumInterval = Duration.ZERO) },
        now = { 0L },
    )

    /**
     * 測試使用的共用 coroutine dispatcher 集合。
     *
     * @param dispatcher 測試排程器。
     */
    private class TestDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
        override val default: CoroutineDispatcher = dispatcher

        override val io: CoroutineDispatcher = dispatcher

        override val main: CoroutineDispatcher = dispatcher

        override val aiDecision: CoroutineDispatcher = dispatcher

        override val aiDecisionParallelism: Int = 1
    }
}
