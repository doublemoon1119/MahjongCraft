package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 驗證指定對局保存狀態的輪詢、終止與 timeout 行為。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryArchiveStatusControllerTest {
    /** 摘要等待既有保存查詢完成後才取得配額，且不再輪詢下一筆。 */
    @Test
    fun `pause drains active status request without sending another poll`() = runTest {
        val transport = FakeStatusTransport(emptyList())
        val controller = HistoryArchiveStatusController(transport, backgroundScope, pollInterval = 10.milliseconds, now = { testScheduler.currentTime.milliseconds })
        controller.watch("match")
        advanceTimeBy(10)
        runCurrent()
        var released = false
        backgroundScope.launch {
            controller.pause()
            released = true
        }
        runCurrent()
        assertEquals(false, released)
        transport.respond(HistoryArchiveStatusDto.PENDING)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        assertTrue(released)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, transport.requests.size)
        assertTrue(transport.cancelled.isEmpty())
        controller.watch("match")
        advanceTimeBy(10)
        runCurrent()
        assertEquals(2, transport.requests.size)
    }

    /** 摘要可安全暫停尚未傳送的輪詢，不必等待整個輪詢週期。 */
    @Test
    fun `pause before first request prevents background polling`() = runTest {
        val transport = FakeStatusTransport(emptyList())
        val controller = HistoryArchiveStatusController(transport, backgroundScope)
        controller.watch("match")
        runCurrent()
        controller.pause()
        advanceTimeBy(2000)
        runCurrent()
        assertTrue(transport.requests.isEmpty())
    }

    /** PENDING 會有限輪詢，收到 SAVED 後停止並呈現完成狀態。 */
    @Test
    fun `pending status eventually resolves and stops polling`() = runTest {
        val transport = FakeStatusTransport(listOf(HistoryArchiveStatusDto.PENDING, HistoryArchiveStatusDto.SAVED))
        val controller = HistoryArchiveStatusController(transport, backgroundScope, 10.milliseconds, maxPolls = 4, responseTimeout = 1.seconds)
        controller.watch("match")
        runCurrent()
        advanceTimeBy(20)
        runCurrent()
        assertEquals(HistoryArchiveStatusView.Resolved(HistoryArchiveStatusDto.SAVED), controller.view.value)
        assertEquals(2, transport.requests.size)
    }

    /** 沒有回覆時會在單次 timeout 後進入失敗狀態並取消 request。 */
    @Test
    fun `missing response times out and cancels request`() = runTest {
        val transport = FakeStatusTransport(emptyList())
        val controller = HistoryArchiveStatusController(transport, backgroundScope, pollInterval = 10.milliseconds, responseTimeout = 50.milliseconds)
        controller.watch("match")
        advanceTimeBy(60)
        runCurrent()
        assertIs<HistoryArchiveStatusView.Failed>(controller.view.value)
        assertTrue(transport.cancelled.isNotEmpty())
    }

    /** 關閉畫面會取消保存狀態監看，不保留舊畫面結果。 */
    @Test
    fun `close cancels active status request`() = runTest {
        val transport = FakeStatusTransport(emptyList())
        val controller = HistoryArchiveStatusController(transport, backgroundScope, pollInterval = 10.milliseconds, responseTimeout = 1.seconds)
        controller.watch("match")
        advanceTimeBy(10)
        runCurrent()
        controller.close()
        assertEquals(HistoryArchiveStatusView.Idle, controller.view.value)
        assertTrue(transport.cancelled.isNotEmpty())
    }

    /** 查詢錯誤不能誤呈現為伺服器權威的 MISSING 狀態。 */
    @Test
    fun `query error is shown as retryable failure`() = runTest {
        val transport = FakeStatusTransport(listOf(HistoryArchiveStatusDto.MISSING), HistoryQueryErrorCodeDto.BUSY)
        val controller = HistoryArchiveStatusController(transport, backgroundScope, pollInterval = 10.milliseconds)
        controller.watch("match")
        advanceTimeBy(10)
        runCurrent()
        assertEquals(HistoryArchiveStatusView.Failed("match"), controller.view.value)
    }

    /** 保存狀態輪詢也遵守伺服器提高的間隔，不沿用短輪詢週期提早送出。 */
    @Test
    fun `test status polling respects increased server interval`() = runTest {
        val transport = FakeStatusTransport(listOf(HistoryArchiveStatusDto.PENDING, HistoryArchiveStatusDto.SAVED))
        val controller = HistoryArchiveStatusController(transport, backgroundScope, pollInterval = 10.milliseconds)
        controller.watch("match")
        runCurrent()
        transport.minimumInterval.value = 100.milliseconds
        advanceTimeBy(99)
        runCurrent()
        assertTrue(transport.requests.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, transport.requests.size)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, transport.requests.size)
        assertEquals(HistoryArchiveStatusView.Resolved(HistoryArchiveStatusDto.SAVED), controller.view.value)
    }

    /**
     * 以測試用 StateFlow 模擬保存狀態傳輸。
     *
     * @property responses 依要求順序回覆的保存狀態。
     * @property errorCode 回覆附帶的查詢錯誤；null 代表成功。
     */
    private class FakeStatusTransport(
        private val responses: List<HistoryArchiveStatusDto>,
        private val errorCode: HistoryQueryErrorCodeDto? = null,
    ) : HistoryArchiveStatusTransport {
        private val mutableState = MutableStateFlow<ClientHistoryArchiveStatusState>(ClientHistoryArchiveStatusState.Idle)
        override val state: StateFlow<ClientHistoryArchiveStatusState> = mutableState
        override val sessionRevision = MutableStateFlow(0L)

        /** 保存狀態輪詢替身保留測試使用的短查詢冷卻。 */
        override val minimumInterval = MutableStateFlow(10.milliseconds)
        val requests = mutableListOf<HistoryArchiveStatusRequestDto>()
        val cancelled = mutableListOf<String>()

        /**
         * 回覆最近一筆仍在途的保存狀態要求。
         *
         * @param status 欲回覆的保存狀態。
         */
        fun respond(status: HistoryArchiveStatusDto) {
            mutableState.value = ClientHistoryArchiveStatusState.Result(HistoryArchiveStatusResponseDto(requests.last().requestId, status))
        }

        override fun query(matchId: String): String {
            val request = HistoryArchiveStatusRequestDto("request-${requests.size}", matchId)
            requests += request
            responses.getOrNull(requests.lastIndex)?.let { status ->
                mutableState.value = ClientHistoryArchiveStatusState.Result(HistoryArchiveStatusResponseDto(request.requestId, status, errorCode))
            }
            return request.requestId
        }

        override fun cancel(requestId: String): Boolean {
            cancelled += requestId
            mutableState.value = ClientHistoryArchiveStatusState.Idle
            return true
        }
    }
}
