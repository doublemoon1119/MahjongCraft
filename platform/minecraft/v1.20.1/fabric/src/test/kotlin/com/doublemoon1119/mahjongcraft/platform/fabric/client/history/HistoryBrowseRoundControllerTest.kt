package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryLimits
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchDetailDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiRedDoraViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledNetworkJson
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** 驗證單局歷史瀏覽的局選擇、事件分頁、牌面導航、快取與過期回應隔離。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryBrowseRoundControllerTest {
    /** 單局查詢控制項在在途或冷卻期間停用，返回摘要不受限制。 */
    @Test
    fun `test round query controls reflect in flight and cooldown without blocking back`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        assertFalse(controller.canQueryRound())
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        assertFalse(controller.canQueryRound())
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertTrue(controller.canQueryRound())
        assertTrue(controller.refreshRound())
        runCurrent()
        assertFalse(controller.canQueryRound())
        assertTrue(controller.backToSummary())
        assertEquals(HistoryBrowsePage.SUMMARY, controller.state.value.page)
        assertFalse(controller.canQueryRound())
    }

    /** 摘要與單局分頁各自保留捲動位置，不會因子頁導航重置。 */
    @Test
    fun `test round reopening preserves summary scroll event cursor and event scroll`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = 2)
        runCurrent()
        assertTrue(controller.nextRoundPage())
        advanceTimeBy(250.milliseconds)
        runCurrent()
        transport.respondEvents(roundNumber = 1, start = 2, next = null)
        runCurrent()
        assertTrue(controller.rememberRoundPosition(73.0))
        assertTrue(controller.backToSummary())
        assertTrue(controller.rememberSummaryPosition(91.0))
        assertFalse(controller.showRound(999))
        assertEquals(HistoryBrowsePage.SUMMARY, controller.state.value.page)
        val sent = transport.roundEventsRequests.size
        assertTrue(controller.showRound(1))
        runCurrent()
        val round = controller.state.value.rounds.getValue(1)
        assertEquals(2, round.eventPageNumber)
        assertEquals(listOf(0, 2), round.eventStartIndices)
        assertEquals(73.0, round.eventScrollOffset)
        assertEquals(91.0, controller.state.value.summary?.scrollOffset)
        assertEquals(sent, transport.roundEventsRequests.size)
    }

    /**
     * 建立列表、摘要與單局頁面的共同前置狀態。
     *
     * @param transport 可手動回覆的模擬傳輸。
     * @param roundNumber 本次選取的局序號。
     * @return 使用虛擬時間的瀏覽控制器。
     */
    private suspend fun TestScope.openRound(
        transport: FakeRoundHistoryTransport,
        roundNumber: Int = 1,
    ): HistoryBrowseController {
        val controller = HistoryBrowseController(transport, backgroundScope, json = bundledNetworkJson(), now = { testScheduler.currentTime.milliseconds })
        controller.open()
        runCurrent()
        transport.respondList(listOf(summary(MATCH_ID)))
        runCurrent()
        assertTrue(controller.showSummary(MATCH_ID))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        transport.respondSummary(detail())
        runCurrent()
        assertTrue(controller.showRound(roundNumber))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        return controller
    }

    /** 牌面帶有規則特徵的事件頁可以存入快取並顯示。 */
    @Test
    fun `test round events with rule tile views are accepted`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        val redFive = TileFaceViewDto(TileDto.Numeric(SuitDto.DOT, 5), listOf(RiichiRedDoraViewDto), "mahjongcraft:riichi/red_five")

        transport.respondEvents(roundNumber = 1, start = 0, tileCatalog = listOf(redFive))
        runCurrent()

        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.rounds.getValue(1).eventsStatus)
    }

    /** 最新局選擇的世代會使先前局的回應失效。 */
    @Test
    fun `test latest round generation rejects stale event response`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        val first = transport.roundEventsRequests.last()
        assertTrue(controller.showRound(2))
        transport.respondEvents(first.requestId, roundNumber = 1, start = 0)
        runCurrent()
        assertEquals(HistoryBrowsePage.ROUND_EVENTS, controller.state.value.page)
        assertEquals(2, controller.state.value.selectedRoundNumber)
        assertEquals(HistoryBrowseStatus.Loading, controller.state.value.rounds.getValue(2).eventsStatus)
    }

    /** 事件頁只有在實際存在下一個起點時才接受下一頁。 */
    @Test
    fun `test round event pagination uses actual next index and short final page`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = 2)
        runCurrent()
        assertTrue(controller.nextRoundPage())
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(2, transport.roundEventsRequests.last().startTransactionIndex)
        transport.respondEvents(roundNumber = 1, start = 2, next = null, transactionIndexes = listOf(2))
        runCurrent()
        assertFalse(controller.nextRoundPage())
        val requestCount = transport.roundEventsRequests.size
        assertTrue(controller.previousRoundPage())
        runCurrent()
        assertEquals(requestCount, transport.roundEventsRequests.size)
        assertEquals(0, controller.state.value.rounds.getValue(1).eventStartIndices.last())
    }

    /** 局面頁依交易總數直接查詢相鄰與首末位置的牌面，不為了確認交易存在而讀事件頁。 */
    @Test
    fun `test state navigation uses the transaction count`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = 2, transactionIndexes = listOf(0, 1), transactionCount = 3)
        runCurrent()
        val eventRequests = transport.roundEventsRequests.size

        assertTrue(controller.showRoundState(HistoryRoundPositionDto.Initial))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        transport.respondState(roundNumber = 1, position = HistoryRoundPositionDto.Initial)
        runCurrent()
        listOf(0, 1, 2).forEach { index ->
            assertTrue(controller.nextRoundState())
            advanceTimeBy(250.milliseconds)
            runCurrent()
            assertEquals(HistoryRoundPositionDto.AfterTransaction(index), transport.roundStateRequests.last().position)
            transport.respondState(roundNumber = 1, position = HistoryRoundPositionDto.AfterTransaction(index))
            runCurrent()
        }
        assertFalse(controller.nextRoundState())
        assertFalse(controller.lastRoundState())

        assertTrue(controller.firstRoundState())
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(HistoryRoundPositionDto.Initial, controller.state.value.rounds.getValue(1).confirmedPosition)
        assertFalse(controller.firstRoundState())

        assertTrue(controller.lastRoundState())
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(HistoryRoundPositionDto.AfterTransaction(2), controller.state.value.rounds.getValue(1).confirmedPosition)
        assertEquals(eventRequests, transport.roundEventsRequests.size)
    }

    /** 和牌明細僅接受已確認交易，並保持事件頁與捲動位置。 */
    @Test
    fun `test inline detail lookup keeps event page and event scroll`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        assertTrue(controller.rememberRoundPosition(42.0))
        advanceTimeBy(250.milliseconds)
        runCurrent()

        assertTrue(controller.loadRoundStateForDetails(0))
        assertEquals(HistoryBrowsePage.ROUND_EVENTS, controller.state.value.page)
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(HistoryRoundPositionDto.AfterTransaction(0), transport.roundStateRequests.last().position)
        transport.respondState(roundNumber = 1, position = HistoryRoundPositionDto.AfterTransaction(0))
        runCurrent()

        val round = controller.state.value.rounds.getValue(1)
        assertEquals(HistoryBrowsePage.ROUND_EVENTS, controller.state.value.page)
        assertEquals(42.0, round.eventScrollOffset)
        assertEquals(HistoryBrowseStatus.Ready, round.eventsStatus)
        assertEquals(HistoryBrowseStatus.Ready, round.stateStatus)
        assertEquals(HistoryRoundPositionDto.AfterTransaction(0), round.state?.position)
    }

    /** 未知交易、冷卻期間與已有在途工作時拒絕明細查詢。 */
    @Test
    fun `test inline detail lookup rejects unknown cooling and in flight requests`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()

        assertFalse(controller.loadRoundStateForDetails(99))
        assertFalse(controller.loadRoundStateForDetails(-1))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertTrue(controller.loadRoundStateForDetails(0))
        assertFalse(controller.loadRoundStateForDetails(0))
    }

    /** 同筆交易的多名贏家共用一次成功的桌況查詢。 */
    @Test
    fun `test inline detail state is shared for multiple winner expansions`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        advanceTimeBy(250.milliseconds)
        runCurrent()

        assertTrue(controller.loadRoundStateForDetails(0))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(1, transport.roundStateRequests.count { it.position == HistoryRoundPositionDto.AfterTransaction(0) })
        transport.respondState(roundNumber = 1, position = HistoryRoundPositionDto.AfterTransaction(0))
        runCurrent()

        val sharedState = controller.state.value.rounds.getValue(1).state
        assertEquals(HistoryRoundPositionDto.AfterTransaction(0), sharedState?.position)
        assertEquals(HistoryBrowsePage.ROUND_EVENTS, controller.state.value.page)
    }

    /** 事件與牌面位置分別保存捲動值，返回局頁時不遺失。 */
    @Test
    fun `test round positions are retained across state and summary navigation`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        assertTrue(controller.rememberRoundPosition(3.5))
        assertTrue(controller.showRoundState(HistoryRoundPositionDto.Initial))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        transport.respondState(roundNumber = 1, position = HistoryRoundPositionDto.Initial)
        runCurrent()
        assertTrue(controller.rememberRoundPosition(7.25))
        assertTrue(controller.backToRound())
        assertEquals(3.5, controller.state.value.rounds.getValue(1).eventScrollOffset)
        assertTrue(controller.backToSummary())
        assertEquals(HistoryBrowsePage.SUMMARY, controller.state.value.page)
    }

    /** 冷卻期間刷新會被拒絕，刷新後則清除局內成功結果並重新要求。 */
    @Test
    fun `test round refresh is cooldown guarded and clears round cache`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        assertFalse(controller.refreshRound())
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertTrue(controller.refreshRound())
        assertEquals(HistoryBrowseStatus.Loading, controller.state.value.rounds.getValue(1).eventsStatus)
    }

    /** 相同局頁再次查閱使用成功快取，不重複傳送要求。 */
    @Test
    fun `test cached round page does not resend`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        val sent = transport.roundEventsRequests.size
        assertTrue(controller.showRoundState(HistoryRoundPositionDto.Initial))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        transport.respondState(roundNumber = 1, position = HistoryRoundPositionDto.Initial)
        runCurrent()
        assertTrue(controller.backToRound())
        assertEquals(sent, transport.roundEventsRequests.size)
    }

    /** 返回摘要再重新開啟同局時，成功事件頁由快取直接恢復。 */
    @Test
    fun `test returning to summary and reopening round uses cache`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        val sent = transport.roundEventsRequests.size
        assertTrue(controller.backToSummary())
        assertTrue(controller.showRound(1))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(sent, transport.roundEventsRequests.size)
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.rounds.getValue(1).eventsStatus)
    }

    /** 世界或連線世代改變時，局內快取與導航狀態一併清除。 */
    @Test
    fun `test session revision invalidates round state`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        transport.sessionRevision.value = 1
        runCurrent()
        assertTrue(controller.state.value.closed)
        assertTrue(controller.state.value.rounds.isEmpty())
    }

    /** 回覆內容與要求位置不符時，不覆蓋既有確認位置。 */
    @Test
    fun `test mismatched state response fails without replacing confirmed position`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        assertTrue(controller.showRoundState(HistoryRoundPositionDto.Initial))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        val request = transport.roundStateRequests.last()
        transport.respondState(1, HistoryRoundPositionDto.AfterTransaction(0), request.requestId)
        runCurrent()
        val round = controller.state.value.rounds.getValue(1)
        assertEquals(null, round.confirmedPosition)
        assertIs<HistoryBrowseStatus.Failed>(round.stateStatus)
    }

    /** 成功快取超過存活時間後，重新進入單局會重新要求事件頁。 */
    @Test
    fun `test expired round cache refetches`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        val sent = transport.roundEventsRequests.size
        advanceTimeBy(30_001.milliseconds)
        assertTrue(controller.backToSummary())
        assertTrue(controller.showRound(1))
        runCurrent()
        assertEquals(sent + 1, transport.roundEventsRequests.size)
    }

    /** 查詢條件變更會清除已選局及其成功資料。 */
    @Test
    fun `test query scope change clears rounds`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        assertTrue(controller.updateQuery(HistoryBrowseQuery(scope = HistoryQueryScopeDto.ALL)))
        assertTrue(controller.state.value.rounds.isEmpty())
        assertEquals(null, controller.state.value.selectedRoundNumber)
    }

    /** 伺服器拒絕單局查詢後，可在冷卻結束時重試同一要求。 */
    @Test
    fun `test failed round query can retry`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEventsError(HistoryQueryErrorCodeDto.BUSY)
        runCurrent()
        assertIs<HistoryBrowseStatus.Failed>(controller.state.value.rounds.getValue(1).eventsStatus)
        advanceTimeBy(250.milliseconds)
        assertTrue(controller.retry())
        runCurrent()
        assertEquals(2, transport.roundEventsRequests.size)
    }

    /** 事件頁可以直接跳到最後一頁再逐頁往前翻，也可以直接回到第一頁。 */
    @Test
    fun `test event pages jump to the first and last page`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = 20, transactionCount = 45)
        runCurrent()
        assertFalse(controller.firstRoundPage())

        assertTrue(controller.lastRoundPage())
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(40, transport.roundEventsRequests.last().startTransactionIndex)
        transport.respondEvents(roundNumber = 1, start = 40, next = null, transactionIndexes = (40 until 45).toList(), transactionCount = 45)
        runCurrent()
        assertEquals(listOf(0, 20, 40), controller.state.value.rounds.getValue(1).eventStartIndices)
        assertFalse(controller.lastRoundPage())

        assertTrue(controller.firstRoundPage())
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(listOf(0), controller.state.value.rounds.getValue(1).eventStartIndices)
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.rounds.getValue(1).eventsStatus)
    }

    /** 回放先要目前這一步起的一段牌面，再在背景讀完整局事件（快取裡的事件頁直接沿用），最後預先要後面的牌面。 */
    @Test
    fun `test replay reads the current state then the whole round then states ahead`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openReplay(transport)

        val first = transport.roundStateRequests.last()
        assertEquals(HistoryRoundPositionDto.Initial, first.position)
        assertEquals(HistoryQueryLimits.MAX_ROUND_STATES, first.count)
        assertEquals(HistoryBrowsePage.ROUND_REPLAY, controller.state.value.page)
        assertEquals(HistoryBrowseStatus.Loading, controller.state.value.replay?.status)

        transport.respondStates()
        runCurrent()
        val ready = assertNotNull(controller.state.value.replay)
        assertEquals(HistoryBrowseStatus.Ready, ready.status)
        assertEquals(HistoryRoundPositionDto.Initial, ready.state?.position)
        assertEquals(TRANSACTION_COUNT + 1, ready.stepCount)
        assertTrue(controller.isReplayStepReady(HistoryQueryLimits.MAX_ROUND_STATES - 1))
        assertFalse(controller.isReplayStepReady(HistoryQueryLimits.MAX_ROUND_STATES))

        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(EVENTS_PAGE, transport.roundEventsRequests.last().startTransactionIndex)
        assertEquals(1, controller.state.value.replay?.eventPages?.size)
        transport.respondEvents(roundNumber = 1, start = EVENTS_PAGE, transactionIndexes = (EVENTS_PAGE until TRANSACTION_COUNT).toList(), transactionCount = TRANSACTION_COUNT)
        runCurrent()
        val complete = assertNotNull(controller.state.value.replay)
        assertEquals(HistoryBrowseStatus.Ready, complete.eventsStatus)
        assertEquals(TRANSACTION_COUNT, complete.eventPages.sumOf { it.transactions.size })

        advanceTimeBy(250.milliseconds)
        runCurrent()
        val second = transport.roundStateRequests.last()
        assertEquals(HistoryRoundPositionDto.AfterTransaction(HistoryQueryLimits.MAX_ROUND_STATES - 1), second.position)
        assertEquals(HistoryQueryLimits.MAX_ROUND_STATES, second.count)
    }

    /** 移到已取得的步數立即顯示且不另外查詢；跳到沒取得的步數時，還沒送出的預先讀取改從該步開始要。 */
    @Test
    fun `test replay moves use received states and request missing steps`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openReplay(transport)
        transport.respondStates()
        runCurrent()
        assertTrue(controller.replayTo(5))
        val nearby = assertNotNull(controller.state.value.replay)
        assertEquals(HistoryBrowseStatus.Ready, nearby.status)
        assertEquals(HistoryRoundPositionDto.AfterTransaction(4), nearby.state?.position)

        advanceTimeBy(250.milliseconds)
        runCurrent()
        transport.respondEvents(roundNumber = 1, start = EVENTS_PAGE, transactionIndexes = (EVENTS_PAGE until TRANSACTION_COUNT).toList(), transactionCount = TRANSACTION_COUNT)
        runCurrent()
        advanceTimeBy(250.milliseconds)
        runCurrent()
        transport.respondStates()
        runCurrent()
        assertTrue(controller.replayTo(TRANSACTION_COUNT))
        val far = assertNotNull(controller.state.value.replay)
        assertEquals(HistoryBrowseStatus.Loading, far.status)
        assertEquals(null, far.state)

        advanceTimeBy(250.milliseconds)
        runCurrent()
        val request = transport.roundStateRequests.last()
        assertEquals(HistoryRoundPositionDto.AfterTransaction(TRANSACTION_COUNT - 1), request.position)
        assertEquals(1, request.count)
        transport.respondStates()
        runCurrent()
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.replay?.status)
    }

    /** 目前這一步讀取失敗時顯示失敗，重試會再要同一段；失敗後不自動接著讀。 */
    @Test
    fun `test failed replay request can retry the same range`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openReplay(transport)
        val failed = transport.roundStateRequests.last()
        transport.respondStatesError(HistoryQueryErrorCodeDto.BUSY)
        runCurrent()
        assertIs<HistoryBrowseStatus.Failed>(controller.state.value.replay?.status)

        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(failed, transport.roundStateRequests.last())
        assertEquals(1, transport.roundEventsRequests.size)
        assertTrue(controller.retry())
        runCurrent()
        val retried = transport.roundStateRequests.last()
        assertEquals(failed.position, retried.position)
        assertEquals(failed.count, retried.count)
        assertEquals(HistoryBrowseStatus.Loading, controller.state.value.replay?.status)
    }

    /** 結束回放回到事件頁，之後才到的回放回覆不會再寫回。 */
    @Test
    fun `test leaving replay discards late states`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openReplay(transport)
        assertTrue(controller.backFromReplay())
        transport.respondStates()
        runCurrent()
        advanceTimeBy(250.milliseconds)
        runCurrent()

        assertEquals(HistoryBrowsePage.ROUND_EVENTS, controller.state.value.page)
        assertEquals(null, controller.state.value.replay)
        assertEquals(1, transport.roundStateRequests.size)
        assertFalse(controller.replayTo(1))
    }

    /** 還不知道交易總數時，等原本在途的事件頁回來後先要第一頁事件，知道後才從指定步數要牌面；不在事件頁時不接受回放。 */
    @Test
    fun `test replay without a known count reads the first event page first`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        assertFalse(controller.replayRound(2))
        assertTrue(controller.showReplay(3))
        assertFalse(controller.showReplay(0))
        transport.respondEvents(roundNumber = 1, start = 0, next = EVENTS_PAGE, transactionCount = TRANSACTION_COUNT)
        runCurrent()
        assertEquals(null, controller.state.value.replay?.stepCount)
        advanceTimeBy(250.milliseconds)
        runCurrent()
        transport.respondRuleSettings()
        runCurrent()
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(2, transport.roundEventsRequests.size)
        assertEquals(null, controller.state.value.replay?.stepCount)

        transport.respondEvents(roundNumber = 1, start = 0, next = EVENTS_PAGE, transactionCount = TRANSACTION_COUNT)
        runCurrent()
        advanceTimeBy(250.milliseconds)
        runCurrent()

        assertEquals(TRANSACTION_COUNT + 1, controller.state.value.replay?.stepCount)
        assertEquals(3, controller.state.value.replay?.step)
        assertEquals(HistoryRoundPositionDto.AfterTransaction(2), transport.roundStateRequests.single().position)
    }

    /** 回放換局停在新一局的初始牌面；返回事件頁時讀取新一局的事件頁。 */
    @Test
    fun `test replay changes round and returns to the new round events`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openReplay(transport)
        transport.respondStates()
        runCurrent()

        assertTrue(controller.replayRound(2))
        val changed = assertNotNull(controller.state.value.replay)
        assertEquals(2, changed.roundNumber)
        assertEquals(0, changed.step)
        assertEquals(null, changed.state)
        assertEquals(2, controller.state.value.selectedRoundNumber)
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(2, transport.roundEventsRequests.last().roundNumber)
        transport.respondEvents(roundNumber = 2, start = 0, transactionIndexes = listOf(0, 1, 2), transactionCount = 3)
        runCurrent()
        advanceTimeBy(250.milliseconds)
        runCurrent()
        val request = transport.roundStateRequests.last()
        assertEquals(2, request.roundNumber)
        assertEquals(HistoryRoundPositionDto.Initial, request.position)
        assertEquals(4, request.count)

        assertTrue(controller.backFromReplay())
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(HistoryBrowsePage.ROUND_EVENTS, controller.state.value.page)
        assertNotNull(controller.state.value.rounds.getValue(2).events)
    }

    /**
     * 開到第一局的事件頁並從初始牌面開始回放，停在第一次回放查詢送出之後。
     *
     * @param transport 可手動回覆的模擬傳輸。
     * @return 使用虛擬時間的瀏覽控制器。
     */
    private suspend fun TestScope.openReplay(transport: FakeRoundHistoryTransport): HistoryBrowseController {
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = EVENTS_PAGE, transactionCount = TRANSACTION_COUNT)
        runCurrent()
        assertTrue(controller.showReplay(0))
        advanceTimeBy(250.milliseconds)
        runCurrent()
        // 擺牌需要規則設定，回放先要規則設定。
        assertEquals(1, transport.ruleSettingsRequests.size)
        transport.respondRuleSettings()
        runCurrent()
        advanceTimeBy(250.milliseconds)
        runCurrent()
        return controller
    }

    /** 關閉控制器會清除局選擇與所有單局狀態。 */
    @Test
    fun `test close clears round state and rejects further navigation`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        controller.close()
        assertTrue(controller.state.value.closed)
        assertTrue(controller.state.value.rounds.isEmpty())
        assertFalse(controller.showRound(1))
        assertFalse(controller.refreshRound())
    }

    /**
     * 建立指定對局的最小列表摘要。
     *
     * @param matchId 對局識別碼。
     * @return 可供控制器進入摘要頁的摘要。
     */
    private fun summary(matchId: String) = HistoryMatchSummaryDto(
        matchId = matchId,
        ruleId = "mahjongcraft:riichi",
        startedAtEpochMillis = 1,
        endedAtEpochMillis = 2,
        outcome = HistoryOutcomeFilterDto.COMPLETED,
        integrity = HistoryIntegrityFilterDto.COMPLETE,
        participants = listOf(HistoryParticipantSummaryDto(0, PLAYER_ID, aiStrategyId = null)),
        roundCount = 2,
        resultsAvailable = true,
        integrityDiagnostic = null,
        duration = null,
        results = emptyList(),
    )

    /**
     * 建立包含兩局索引的摘要內容。
     *
     * @return 含局索引的摘要。
     */
    private fun detail() = HistoryMatchDetailDto(
        summary = summary(MATCH_ID),
        rounds = listOf(HistoryRoundSummaryDto(1, 1, 2), HistoryRoundSummaryDto(2, 3, 4)),
    )

    /**
     * 建立最小且可通過回應驗證的事件頁。
     *
     * @param roundNumber 局序號。
     * @param indexes 此頁交易索引。
     * @param next 下一頁起始交易索引。
     * @param tileCatalog 本頁的牌面目錄；每筆交易都宣告到目錄的全部牌。
     * @param transactionCount 本局交易總數；預設為剛好涵蓋本頁與下一頁起點。
     * @return 事件頁 DTO。
     */
    private fun events(
        roundNumber: Int,
        indexes: List<Int>,
        next: Int?,
        tileCatalog: List<TileFaceViewDto> = emptyList(),
        transactionCount: Int = next?.plus(1) ?: (indexes.lastOrNull()?.plus(1) ?: 0),
    ) = HistoryRoundEventsDto(
        identity = HistoryReplayIdentityDto(MATCH_ID, TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, PLAYER_ID, null))),
        roundNumber = roundNumber,
        transactions = indexes.map { HistoryReplayTransactionDto(it, it.toLong(), it == 0, tileCatalog.size, emptyList()) },
        nextTransactionIndex = next,
        transactionCount = transactionCount,
        tileCatalog = tileCatalog,
    )

    /**
     * 建立最小且可通過回應驗證的桌況。
     *
     * @param roundNumber 局序號。
     * @param position 桌況對應的局內位置。
     * @return 桌況 DTO。
     */
    private fun state(roundNumber: Int, position: HistoryRoundPositionDto) = HistoryRoundStateDto(
        identity = HistoryReplayIdentityDto(MATCH_ID, TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, PLAYER_ID, null))),
        roundNumber = roundNumber,
        position = position,
        tileCatalog = emptyList(),
        players = listOf(HistoryReplayPlayerStateDto(0, emptyList(), emptyList(), null, emptyList(), 25000, WindDto.EAST, emptyList())),
        wallTiles = emptyList(),
        reservedTiles = emptyList(),
        currentPlayerSeat = 0,
        dealerSeat = 0,
        prevalentWind = WindDto.EAST,
        roundPosition = MatchRoundPositionDto(0, WindDto.EAST, 1, MatchRoundPhaseDto.REGULAR),
        comboCount = 0,
        finishedPlayerSeats = emptySet(),
        hasPendingReaction = false,
        hasPendingRobbingReaction = false,
        outcome = null,
        wallPlacements = null,
        discardOrder = emptyList(),
        tilePoses = emptyMap(),
        presentationNotes = null,
    )

    /** 模擬局內查詢傳輸，只暴露要求與手動回覆。 */
    private inner class FakeRoundHistoryTransport : HistoryQueryTransport {
        /** 可觀察的傳輸結果。 */
        private val mutableState = MutableStateFlow<ClientHistoryQueryState>(ClientHistoryQueryState.Idle)

        /** 可觀察的傳輸結果流程。 */
        override val state: StateFlow<ClientHistoryQueryState> = mutableState.asStateFlow()

        /** 模擬連線世代。 */
        override val sessionRevision = MutableStateFlow(0L)

        /** 模擬伺服器冷卻間隔。 */
        override val minimumInterval = MutableStateFlow(250.milliseconds)

        /** 已送出的事件頁要求。 */
        val roundEventsRequests = mutableListOf<HistoryRoundEventsRequestDto>()

        /** 已送出的桌況要求。 */
        val roundStateRequests = mutableListOf<HistoryRoundStateRequestDto>()

        /** 測試用要求序號。 */
        private var nextId = 0

        /**
         * 記錄列表查詢並回報等待狀態。
         *
         * @param request 列表要求。
         * @return 模擬配對識別碼。
         */
        override fun queryList(request: HistoryListRequestDto): String = "list-${++nextId}".also {
            mutableState.value = ClientHistoryQueryState.Loading(it)
        }

        /**
         * 記錄摘要查詢並回報等待狀態。
         *
         * @param request 摘要要求。
         * @return 模擬配對識別碼。
         */
        override fun querySummary(request: HistorySummaryRequestDto): String = "summary-${++nextId}".also {
            mutableState.value = ClientHistoryQueryState.Loading(it)
        }

        /**
         * 記錄規則設定要求並回報等待狀態。
         *
         * @param request 規則設定要求。
         * @return 模擬配對識別碼。
         */
        override fun queryRuleSettings(request: HistoryRuleSettingsRequestDto): String = "rules-${++nextId}".also {
            ruleSettingsRequests += it
            mutableState.value = ClientHistoryQueryState.Loading(it)
        }

        /** 已送出的規則設定要求識別碼。 */
        val ruleSettingsRequests = mutableListOf<String>()

        /** 回覆最後一筆規則設定要求。 */
        fun respondRuleSettings() {
            val config = GameConfig(RiichiRuleConfig()).toDto(registries = DefaultNetworkDtoRegistries().apply { registerBundledNetworkDtos() })
            mutableState.value = ClientHistoryQueryState.RuleSettingsResult(HistoryRuleSettingsResponseDto(ruleSettingsRequests.last(), config, errorCode = null))
        }

        /**
         * 記錄事件頁查詢。
         *
         * @param request 事件頁要求。
         * @return 測試用要求識別碼。
         */
        override fun queryRoundEvents(request: HistoryRoundEventsRequestDto): String = request.copy(requestId = "events-${++nextId}").also {
            roundEventsRequests += it
            mutableState.value = ClientHistoryQueryState.Loading(it.requestId)
        }.requestId

        /**
         * 記錄桌況查詢。
         *
         * @param request 桌況要求。
         * @return 測試用要求識別碼。
         */
        override fun queryRoundState(request: HistoryRoundStateRequestDto): String = request.copy(requestId = "state-${++nextId}").also {
            roundStateRequests += it
            mutableState.value = ClientHistoryQueryState.Loading(it.requestId)
        }.requestId

        /**
         * 模擬取消要求。
         *
         * @param requestId 要取消的要求識別碼。
         * @return 一律表示已接受取消。
         */
        override fun cancel(requestId: String): Boolean = true

        /**
         * 發出列表成功回應。
         *
         * @param entries 列表摘要。
         */
        fun respondList(entries: List<HistoryMatchSummaryDto>) {
            val listResponse = HistoryListResponseDto(
                requestId = "list-1",
                entries = entries,
                nextCursor = null,
                previousCursor = null,
                totalCount = 0,
                firstEntryIndex = 1,
                errorCode = null,
                allowAll = false,
                allowStressTest = false,
            )
            mutableState.value = ClientHistoryQueryState.ListResult(listResponse)
        }

        /**
         * 發出摘要成功回應。
         *
         * @param detail 摘要內容。
         */
        fun respondSummary(detail: HistoryMatchDetailDto) {
            mutableState.value = ClientHistoryQueryState.SummaryResult(HistorySummaryResponseDto("summary-2", detail, errorCode = null))
        }

        /**
         * 發出事件頁成功回應。
         *
         * @param requestId 回應配對識別碼。
         * @param roundNumber 局序號。
         * @param start 事件頁起點。
         * @param next 下一頁起點。
         * @param transactionIndexes 此頁交易索引；null 時依起點與下一頁推導。
         * @param tileCatalog 此頁的牌面目錄。
         * @param transactionCount 本局交易總數；null 時剛好涵蓋本頁與下一頁起點。
         */
        fun respondEvents(
            requestId: String = roundEventsRequests.last().requestId,
            roundNumber: Int,
            start: Int,
            next: Int? = null,
            transactionIndexes: List<Int>? = null,
            tileCatalog: List<TileFaceViewDto> = emptyList(),
            transactionCount: Int? = null,
        ) {
            val indexes = transactionIndexes ?: if (next == null) listOf(start) else (start until next).toList()
            val events = if (transactionCount == null) events(roundNumber, indexes, next, tileCatalog) else events(roundNumber, indexes, next, tileCatalog, transactionCount)
            mutableState.value = ClientHistoryQueryState.RoundEventsResult(
                response = HistoryRoundEventsResponseDto(requestId, MATCH_ID, roundNumber, start, events, errorCode = null),
            )
        }

        /**
         * 回覆單局查詢的穩定錯誤。
         *
         * @param errorCode 伺服器拒絕原因。
         * @param requestId 回覆配對識別碼。
         */
        fun respondEventsError(errorCode: HistoryQueryErrorCodeDto, requestId: String = roundEventsRequests.last().requestId) {
            val request = roundEventsRequests.last { it.requestId == requestId }
            mutableState.value = ClientHistoryQueryState.RoundEventsResult(
                response = HistoryRoundEventsResponseDto(requestId, request.matchId, request.roundNumber, request.startTransactionIndex, errorCode = errorCode, events = null),
            )
        }

        /**
         * 發出桌況成功回應。
         *
         * @param roundNumber 局序號。
         * @param position 回應位置。
         * @param requestId 回應配對識別碼。
         */
        fun respondState(roundNumber: Int, position: HistoryRoundPositionDto, requestId: String = roundStateRequests.last().requestId) {
            mutableState.value = ClientHistoryQueryState.RoundStateResult(
                response = HistoryRoundStateResponseDto(requestId, MATCH_ID, roundNumber, position, listOf(state(roundNumber, position)), errorCode = null),
            )
        }

        /**
         * 依最後一筆桌況要求回覆要求數量的連續桌況。
         *
         * @param request 要回覆的桌況要求。
         */
        fun respondStates(request: HistoryRoundStateRequestDto = roundStateRequests.last()) {
            val states = (0 until request.count).map { offset ->
                val position = when (val start = request.position) {
                    HistoryRoundPositionDto.Initial -> if (offset == 0) start else HistoryRoundPositionDto.AfterTransaction(offset - 1)
                    is HistoryRoundPositionDto.AfterTransaction -> HistoryRoundPositionDto.AfterTransaction(start.index + offset)
                }
                state(request.roundNumber, position)
            }
            mutableState.value = ClientHistoryQueryState.RoundStateResult(
                response = HistoryRoundStateResponseDto(request.requestId, MATCH_ID, request.roundNumber, request.position, states, errorCode = null),
            )
        }

        /**
         * 回覆最後一筆桌況要求的穩定錯誤。
         *
         * @param errorCode 伺服器拒絕原因。
         */
        fun respondStatesError(errorCode: HistoryQueryErrorCodeDto) {
            val request = roundStateRequests.last()
            mutableState.value = ClientHistoryQueryState.RoundStateResult(
                response = HistoryRoundStateResponseDto(request.requestId, MATCH_ID, request.roundNumber, request.position, emptyList(), errorCode = errorCode),
            )
        }
    }

    /** 測試對局、牌桌與玩家的穩定身份。 */
    private companion object {
        /** 測試用對局 UUID。 */
        const val MATCH_ID = "00000000-0000-0000-0000-000000000001"

        /** 測試用牌桌 UUID。 */
        const val TABLE_ID = "00000000-0000-0000-0000-000000000002"

        /** 測試用玩家 UUID。 */
        const val PLAYER_ID = "00000000-0000-0000-0000-000000000003"

        /** 回放測試的本局交易總數。 */
        const val TRANSACTION_COUNT = 40

        /** 回放測試的事件頁大小。 */
        const val EVENTS_PAGE = HistoryRoundNavigation.EVENTS_PAGE_SIZE
    }
}
