package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

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
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
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
        advanceTimeBy(250)
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
        advanceTimeBy(250)
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

    /** 建立列表、摘要與單局頁面的共同前置狀態。
     * @param transport 可手動回覆的模擬傳輸。
     * @param roundNumber 本次選取的局序號。
     * @return 使用虛擬時間的瀏覽控制器。
     */
    private suspend fun TestScope.openRound(
        transport: FakeRoundHistoryTransport,
        roundNumber: Int = 1,
    ): HistoryBrowseController {
        val controller = HistoryBrowseController(transport, backgroundScope, now = { testScheduler.currentTime.milliseconds })
        controller.open()
        runCurrent()
        transport.respondList(listOf(summary(MATCH_ID)))
        runCurrent()
        assertTrue(controller.showSummary(MATCH_ID))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail())
        runCurrent()
        assertTrue(controller.showRound(roundNumber))
        advanceTimeBy(250)
        runCurrent()
        return controller
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
        advanceTimeBy(250)
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

    /** 相鄰牌面位置會先確認跨頁交易，再查詢該交易後的桌況。 */
    @Test
    fun `test state navigation chains event existence across pages`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = 2, transactionIndexes = listOf(0, 1))
        runCurrent()
        assertTrue(controller.showRoundState(HistoryRoundPositionDto.Initial))
        advanceTimeBy(250)
        runCurrent()
        transport.respondState(roundNumber = 1, position = HistoryRoundPositionDto.Initial)
        runCurrent()
        assertTrue(controller.nextRoundState())
        advanceTimeBy(250)
        runCurrent()
        transport.respondState(roundNumber = 1, position = HistoryRoundPositionDto.AfterTransaction(0))
        runCurrent()
        assertEquals(HistoryRoundPositionDto.AfterTransaction(0), controller.state.value.rounds.getValue(1).confirmedPosition)
        assertTrue(controller.nextRoundState())
        advanceTimeBy(250)
        runCurrent()
        transport.respondState(roundNumber = 1, position = HistoryRoundPositionDto.AfterTransaction(1))
        runCurrent()
        assertEquals(HistoryRoundPositionDto.AfterTransaction(1), controller.state.value.rounds.getValue(1).confirmedPosition)
        assertTrue(controller.nextRoundState())
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, transport.roundEventsRequests.last().startTransactionIndex)
    }

    /** 和牌明細僅接受已確認交易，並保持事件頁與捲動位置。 */
    @Test
    fun `test inline detail lookup keeps event page and event scroll`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = null)
        runCurrent()
        assertTrue(controller.rememberRoundPosition(42.0))
        advanceTimeBy(250)
        runCurrent()

        assertTrue(controller.loadRoundStateForDetails(0))
        assertEquals(HistoryBrowsePage.ROUND_EVENTS, controller.state.value.page)
        advanceTimeBy(250)
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
        advanceTimeBy(250)
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
        advanceTimeBy(250)
        runCurrent()

        assertTrue(controller.loadRoundStateForDetails(0))
        advanceTimeBy(250)
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
        advanceTimeBy(250)
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
        advanceTimeBy(250)
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
        advanceTimeBy(250)
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
        advanceTimeBy(250)
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
        advanceTimeBy(250)
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
        advanceTimeBy(30_001)
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
        advanceTimeBy(250)
        assertTrue(controller.retry())
        runCurrent()
        assertEquals(2, transport.roundEventsRequests.size)
    }

    /** 牌面跨頁探測失敗時，只標記牌面要求失敗並保留已成功事件頁。 */
    @Test
    fun `test state probe failure preserves ready events`() = runTest {
        val transport = FakeRoundHistoryTransport()
        val controller = openRound(transport)
        transport.respondEvents(roundNumber = 1, start = 0, next = 1, transactionIndexes = listOf(0))
        runCurrent()
        assertTrue(controller.showRoundState(HistoryRoundPositionDto.Initial))
        advanceTimeBy(250)
        runCurrent()
        transport.respondState(1, HistoryRoundPositionDto.Initial)
        runCurrent()
        assertTrue(controller.nextRoundState())
        advanceTimeBy(250)
        runCurrent()
        transport.respondState(1, HistoryRoundPositionDto.AfterTransaction(0))
        runCurrent()
        assertTrue(controller.nextRoundState())
        advanceTimeBy(250)
        runCurrent()
        transport.respondEventsError(HistoryQueryErrorCodeDto.NOT_AVAILABLE)
        runCurrent()
        val round = controller.state.value.rounds.getValue(1)
        assertEquals(HistoryBrowseStatus.Ready, round.eventsStatus)
        assertIs<HistoryBrowseStatus.Failed>(round.stateStatus)
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

    /** 建立指定對局的最小列表摘要。
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
        participants = listOf(HistoryParticipantSummaryDto(0, PLAYER_ID)),
        roundCount = 2,
        resultsAvailable = true,
    )

    /** 建立包含兩局索引的摘要內容。
     * @return 含局索引的摘要。
     */
    private fun detail() = HistoryMatchDetailDto(
        summary(MATCH_ID),
        listOf(HistoryRoundSummaryDto(1, 1, 2), HistoryRoundSummaryDto(2, 3, 4)),
    )

    /** 建立最小且可通過回應驗證的事件頁。
     * @param roundNumber 局序號。
     * @param indexes 此頁交易索引。
     * @param next 下一頁起始交易索引。
     * @return 事件頁 DTO。
     */
    private fun events(roundNumber: Int, indexes: List<Int>, next: Int?) = HistoryRoundEventsDto(
        identity = HistoryReplayIdentityDto(MATCH_ID, TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, PLAYER_ID, null))),
        roundNumber = roundNumber,
        transactions = indexes.map { HistoryReplayTransactionDto(it, it.toLong(), it == 0, 0, emptyList()) },
        nextTransactionIndex = next,
        tileCatalog = emptyList(),
    )

    /** 建立最小且可通過回應驗證的桌況。
     * @param roundNumber 局序號。
     * @param position 桌況對應的局內位置。
     * @return 桌況 DTO。
     */
    private fun state(roundNumber: Int, position: HistoryRoundPositionDto) = HistoryRoundStateDto(
        identity = HistoryReplayIdentityDto(MATCH_ID, TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, PLAYER_ID, null))),
        roundNumber = roundNumber,
        position = position,
        tileCatalog = emptyList(),
        players = listOf(HistoryReplayPlayerStateDto(0, emptyList(), emptyList(), null, emptyList(), 25000, WindDto.EAST, null, emptyList())),
        wallTiles = emptyList(),
        reservedTiles = emptyList(),
        currentPlayerSeat = 0,
        dealerSeat = 0,
        prevalentWind = WindDto.EAST,
        roundPosition = MatchRoundPositionDto(0, WindDto.EAST, 1, MatchRoundPhaseDto.REGULAR),
        comboCount = 0,
        finishedPlayerSeats = emptySet(),
        dynamicRuleState = null,
        hasPendingReaction = false,
        hasPendingRobbingReaction = false,
        outcome = null,
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

        /** 記錄列表查詢並回報等待狀態。
         * @param request 列表要求。
         * @return 模擬配對識別碼。
         */
        override fun queryList(request: HistoryListRequestDto): String = "list-${++nextId}".also {
            mutableState.value = ClientHistoryQueryState.Loading(it)
        }

        /** 記錄摘要查詢並回報等待狀態。
         * @param request 摘要要求。
         * @return 模擬配對識別碼。
         */
        override fun querySummary(request: HistorySummaryRequestDto): String = "summary-${++nextId}".also {
            mutableState.value = ClientHistoryQueryState.Loading(it)
        }

        /** 記錄規則設定查詢。
         * @param request 規則設定要求。
         * @return 模擬配對識別碼。
         */
        override fun queryRuleSettings(request: HistoryRuleSettingsRequestDto): String = "rules-${++nextId}"

        /** 記錄事件頁查詢。
         * @param request 事件頁要求。
         * @return 測試用要求識別碼。
         */
        override fun queryRoundEvents(request: HistoryRoundEventsRequestDto): String = request.copy(requestId = "events-${++nextId}").also {
            roundEventsRequests += it
            mutableState.value = ClientHistoryQueryState.Loading(it.requestId)
        }.requestId

        /** 記錄桌況查詢。
         * @param request 桌況要求。
         * @return 測試用要求識別碼。
         */
        override fun queryRoundState(request: HistoryRoundStateRequestDto): String = request.copy(requestId = "state-${++nextId}").also {
            roundStateRequests += it
            mutableState.value = ClientHistoryQueryState.Loading(it.requestId)
        }.requestId

        /** 模擬取消要求。
         * @param requestId 要取消的要求識別碼。
         * @return 一律表示已接受取消。
         */
        override fun cancel(requestId: String): Boolean = true

        /** 發出列表成功回應。
         * @param entries 列表摘要。
         */
        fun respondList(entries: List<HistoryMatchSummaryDto>) {
            mutableState.value = ClientHistoryQueryState.ListResult(HistoryListResponseDto("list-1", entries))
        }

        /** 發出摘要成功回應。
         * @param detail 摘要內容。
         */
        fun respondSummary(detail: HistoryMatchDetailDto) {
            mutableState.value = ClientHistoryQueryState.SummaryResult(HistorySummaryResponseDto("summary-2", detail))
        }

        /** 發出事件頁成功回應。
         * @param requestId 回應配對識別碼。
         * @param roundNumber 局序號。
         * @param start 事件頁起點。
         * @param next 下一頁起點。
         * @param transactionIndexes 此頁交易索引；null 時依起點與下一頁推導。
         */
        fun respondEvents(
            requestId: String = roundEventsRequests.last().requestId,
            roundNumber: Int,
            start: Int,
            next: Int? = null,
            transactionIndexes: List<Int>? = null,
        ) {
            val indexes = transactionIndexes ?: if (next == null) listOf(start) else (start until next).toList()
            mutableState.value = ClientHistoryQueryState.RoundEventsResult(
                HistoryRoundEventsResponseDto(requestId, MATCH_ID, roundNumber, start, events(roundNumber, indexes, next)),
            )
        }

        /** 回覆單局查詢的穩定錯誤。
         * @param errorCode 伺服器拒絕原因。
         * @param requestId 回覆配對識別碼。
         */
        fun respondEventsError(errorCode: HistoryQueryErrorCodeDto, requestId: String = roundEventsRequests.last().requestId) {
            val request = roundEventsRequests.last { it.requestId == requestId }
            mutableState.value = ClientHistoryQueryState.RoundEventsResult(
                HistoryRoundEventsResponseDto(requestId, request.matchId, request.roundNumber, request.startTransactionIndex, errorCode = errorCode),
            )
        }

        /** 發出桌況成功回應。
         * @param roundNumber 局序號。
         * @param position 回應位置。
         * @param requestId 回應配對識別碼。
         */
        fun respondState(roundNumber: Int, position: HistoryRoundPositionDto, requestId: String = roundStateRequests.last().requestId) {
            mutableState.value = ClientHistoryQueryState.RoundStateResult(
                HistoryRoundStateResponseDto(requestId, MATCH_ID, roundNumber, position, state(roundNumber, position)),
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
    }
}
