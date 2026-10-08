package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
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
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortFieldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

/** 驗證歷史瀏覽控制器的查詢排程、導覽快取、失敗處理與生命週期。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryBrowseControllerTest {
    /** 短頁依實際筆數累計範圍，返回上一頁及刷新後不沿用錯誤偏移。 */
    @Test
    fun `test page ranges count actual short pages and reset on refresh`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        assertFalse(controller.canRefresh())
        transport.respondList(entries = List(7) { summary("first-$it") }, nextCursor = "second")
        runCurrent()
        assertEquals(1, controller.state.value.list.firstEntryIndex)
        assertTrue(controller.nextPage())
        advanceTimeBy(250)
        runCurrent()
        transport.respondList(entries = List(3) { summary("second-$it") }, nextCursor = "third")
        runCurrent()
        assertEquals(8, controller.state.value.list.firstEntryIndex)
        assertTrue(controller.nextPage())
        advanceTimeBy(250)
        runCurrent()
        transport.respondList(entries = List(2) { summary("third-$it") })
        runCurrent()
        assertEquals(11, controller.state.value.list.firstEntryIndex)
        assertTrue(controller.previousPage())
        advanceTimeBy(250)
        runCurrent()
        transport.respondList(entries = List(3) { summary("second-$it") }, nextCursor = "third")
        runCurrent()
        assertEquals(8, controller.state.value.list.firstEntryIndex)
        advanceTimeBy(250)
        runCurrent()
        assertTrue(controller.refresh())
        runCurrent()
        transport.respondList(entries = listOf(summary("fresh")))
        runCurrent()
        assertEquals(1, controller.state.value.list.firstEntryIndex)
    }

    /** 全部紀錄入口由權威能力回覆開啟，權限撤銷後切回自己的紀錄。 */
    @Test
    fun `test administrator capability is revoked without retaining all scope`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(allowAll = true)
        runCurrent()
        assertTrue(controller.state.value.allowAll)
        controller.updateQuery(HistoryBrowseQuery(scope = HistoryQueryScopeDto.ALL))
        advanceTimeBy(300)
        runCurrent()
        assertEquals(HistoryQueryScopeDto.ALL, transport.listRequests.last().scope)
        transport.respondList(errorCode = HistoryQueryErrorCodeDto.ACCESS_DENIED, allowAll = false)
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(HistoryQueryScopeDto.OWN, controller.state.value.query.scope)
        assertEquals(HistoryQueryScopeDto.OWN, transport.listRequests.last().scope)
        assertEquals(false, controller.state.value.allowAll)
    }

    /** 首次開啟會送出第一頁並提交成功清單。 */
    @Test
    fun `test initial open queries first page`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)

        controller.open()
        runCurrent()
        assertEquals(1, transport.listRequests.size)
        assertEquals(null, transport.listRequests.single().cursor)

        transport.respondList(entries = listOf(summary("first")), nextCursor = "next")
        runCurrent()
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.list.status)
        assertEquals(listOf("first"), controller.state.value.list.entries.map { it.matchId })
    }

    /** 300 毫秒去抖只送出最後條件，且在途回應不能覆蓋新條件。 */
    @Test
    fun `test debounced latest conditions keep one in flight and reject stale completion`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        val firstRequest = transport.listRequests.single()

        controller.updateQuery(HistoryBrowseQuery(sortDirection = HistorySortDirectionDto.ASC))
        controller.updateQuery(HistoryBrowseQuery(sortField = HistorySortFieldDto.DURATION))
        advanceTimeBy(299)
        runCurrent()
        assertEquals(1, transport.listRequests.size)

        transport.respondList(firstRequest.requestId, listOf(summary("stale")))
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(2, transport.listRequests.size)
        assertEquals(HistorySortFieldDto.DURATION, transport.listRequests.last().sortField)
        transport.respondList(entries = listOf(summary("latest")))
        runCurrent()
        assertEquals(listOf("latest"), controller.state.value.list.entries.map { it.matchId })
    }

    /** 下一頁游標只有在成功回應後才提交，失敗時保留原頁與游標堆疊。 */
    @Test
    fun `test pagination commits cursors only after successful response`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("one")), nextCursor = "cursor-1")
        runCurrent()
        assertEquals(1, controller.state.value.list.firstEntryIndex)

        assertTrue(controller.nextPage())
        advanceTimeBy(250)
        runCurrent()
        assertEquals("cursor-1", transport.listRequests.last().cursor)
        transport.respondList(errorCode = HistoryQueryErrorCodeDto.BUSY)
        runCurrent()
        assertEquals(HistoryBrowseStatus.Failed(HistoryBrowseFailure.BUSY), controller.state.value.list.status)
        assertEquals(1, controller.state.value.list.pageNumber)
        assertTrue(controller.retry())
        advanceTimeBy(250)
        runCurrent()
        transport.respondList(entries = listOf(summary("two")), nextCursor = null)
        runCurrent()
        assertEquals(2, controller.state.value.list.pageNumber)
        assertEquals(2, controller.state.value.list.firstEntryIndex)
        assertEquals(listOf("two"), controller.state.value.list.entries.map { it.matchId })
    }

    /** 上一頁失敗時保留原游標堆疊，成功重試後才返回上一頁。 */
    @Test
    fun `test previous page failure preserves stack until retry succeeds`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("one")), nextCursor = "cursor-1")
        runCurrent()
        assertTrue(controller.nextPage())
        advanceTimeBy(250)
        runCurrent()
        transport.respondList(entries = listOf(summary("two")), nextCursor = null)
        runCurrent()

        assertTrue(controller.previousPage())
        advanceTimeBy(250)
        runCurrent()
        transport.respondList(errorCode = HistoryQueryErrorCodeDto.BUSY)
        runCurrent()
        assertEquals(2, controller.state.value.list.pageNumber)
        assertTrue(controller.retry())
        advanceTimeBy(250)
        runCurrent()
        assertEquals(null, transport.listRequests.last().cursor)
        transport.respondList(entries = listOf(summary("one")), nextCursor = "cursor-1")
        runCurrent()
        assertEquals(1, controller.state.value.list.pageNumber)
    }

    /** 摘要在途返回列表後不可刷新，完成並經過冷卻後才接受新清單要求。 */
    @Test
    fun `test refresh after returning waits for active summary and cooldown`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        val summaryRequest = transport.summaryRequests.last().requestId
        assertTrue(controller.backToList())
        assertFalse(controller.refresh())
        runCurrent()
        transport.emit(ClientHistoryQueryState.SummaryResult(HistorySummaryResponseDto(summaryRequest, detail("match"))))
        runCurrent()
        assertEquals(1, transport.listRequests.size)
        assertFalse(controller.refresh())
        advanceTimeBy(250)
        runCurrent()
        assertTrue(controller.refresh())
        runCurrent()
        transport.respondList(entries = emptyList())
        runCurrent()
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.list.status)
        assertTrue(controller.state.value.list.entries.isEmpty())
    }

    /** 成功空清單是有效結果，且不會被當成查詢失敗。 */
    @Test
    fun `test empty successful response is ready`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = emptyList())
        runCurrent()
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.list.status)
        assertTrue(controller.state.value.list.entries.isEmpty())
    }

    /** 傳送間隔在 249 毫秒時仍等待，滿 250 毫秒才允許下一項要求。 */
    @Test
    fun `test minimum interval waits until 250 milliseconds`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("old")))
        runCurrent()
        assertEquals(false, controller.canRefresh())
        assertTrue(controller.isRefreshCoolingDown())
        advanceTimeBy(249)
        runCurrent()
        assertEquals(false, controller.canRefresh())
        assertEquals(1, transport.listRequests.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(false, controller.isRefreshCoolingDown())
        assertTrue(controller.canRefresh())
        assertTrue(controller.refresh())
        runCurrent()
        assertEquals(2, transport.listRequests.size)
    }

    /** 查詢冷卻設定重新載入後，控制器會立即套用縮短或延長的間隔。 */
    @Test
    fun `test minimum interval reload changes refresh availability`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("old")))
        runCurrent()

        transport.minimumInterval.value = 500.milliseconds
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertFalse(controller.canRefresh())

        transport.minimumInterval.value = 100.milliseconds
        runCurrent()
        assertTrue(controller.canRefresh())
        assertTrue(controller.refresh())
        runCurrent()
    }

    /** 尚未送出的最新查詢會依重新載入的間隔重新排程。 */
    @Test
    fun `test pending query reschedules when minimum interval changes`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("old")), nextCursor = "next")
        runCurrent()
        assertTrue(controller.nextPage())
        runCurrent()
        transport.minimumInterval.value = 1_000.milliseconds
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, transport.listRequests.size)
        transport.minimumInterval.value = 600.milliseconds
        runCurrent()
        advanceTimeBy(99)
        runCurrent()
        assertEquals(1, transport.listRequests.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, transport.listRequests.size)
        assertEquals("next", transport.listRequests.last().cursor)
    }

    /** 刷新會保留條件並從第一頁重新查詢。 */
    @Test
    fun `test refresh queries first page`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("old")), nextCursor = "next")
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertTrue(controller.refresh())
        runCurrent()
        assertEquals(null, transport.listRequests.last().cursor)
        transport.respondList(entries = listOf(summary("new")))
        runCurrent()
        assertEquals(listOf("new"), controller.state.value.list.entries.map { it.matchId })
    }

    /** 摘要查詢先等待同 session 的保存狀態查詢釋放配額。 */
    @Test
    fun `summary waits for query coordination before sending`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = HistoryBrowseController(transport, backgroundScope, now = { testScheduler.currentTime.milliseconds }, beforeDetailQuery = { delay(500.milliseconds) })
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(749)
        runCurrent()
        assertTrue(transport.summaryRequests.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, transport.summaryRequests.size)
    }

    /** 等待外部查詢期間返回列表，不再送出摘要或丟失列表位置。 */
    @Test
    fun `return during summary coordination cancels queued query`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = HistoryBrowseController(transport, backgroundScope, now = { testScheduler.currentTime.milliseconds }, beforeDetailQuery = { delay(500.milliseconds) })
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        controller.rememberListPosition(42.5, "match")
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        assertTrue(controller.backToList())
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(transport.summaryRequests.isEmpty())
        assertEquals(42.5, controller.state.value.list.scrollOffset)
        assertEquals(1, transport.listRequests.size)
    }

    /** 返回列表後重開相同摘要會使用成功快取，不重新傳送要求。 */
    @Test
    fun `test summary back uses cache without requery`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail("match"))
        runCurrent()
        val summaryRequests = transport.summaryRequests.size
        assertTrue(controller.backToList())
        assertTrue(controller.showSummary("match"))
        assertEquals(summaryRequests, transport.summaryRequests.size)
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.summary?.status)
    }

    /** 返回列表時保留先前記錄的捲動位置。 */
    @Test
    fun `test summary back retains list scroll position`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.rememberListPosition(42.5, "match"))
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail("match"))
        runCurrent()
        assertTrue(controller.backToList())
        assertEquals(42.5, controller.state.value.list.scrollOffset)
    }

    /** 摘要頁位置只接受有限非負值，返回摘要時保留位置。 */
    @Test
    fun `summary position is validated and retained`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail("match"))
        runCurrent()

        assertFalse(controller.rememberSummaryPosition(-1.0))
        assertFalse(controller.rememberSummaryPosition(Double.NaN))
        assertTrue(controller.rememberSummaryPosition(17.25))
        assertTrue(controller.backToList())
        assertTrue(controller.showSummary("match"))
        assertEquals(17.25, controller.state.value.summary?.scrollOffset)
    }

    /** 摘要回覆缺少內容或對局識別碼不符時會回報無法取得。 */
    @Test
    fun `test malformed summary response is not available`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(null)
        runCurrent()
        assertEquals(HistoryBrowseFailure.NOT_AVAILABLE, assertIs<HistoryBrowseStatus.Failed>(controller.state.value.summary?.status).reason)
    }

    /** 規則設定頁沿用摘要對局，並在缺少設定時保留明確失敗狀態。 */
    @Test
    fun `rule settings page queries selected summary and supports back`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail("match"))
        runCurrent()

        assertTrue(controller.showRuleSettings())
        advanceTimeBy(250)
        runCurrent()
        assertEquals(1, transport.ruleSettingsRequests.size)
        transport.respondRuleSettings()
        runCurrent()
        assertEquals(HistoryBrowsePage.RULE_SETTINGS, controller.state.value.page)
        assertEquals(
            HistoryBrowseFailure.NOT_AVAILABLE,
            assertIs<HistoryBrowseStatus.Failed>(controller.state.value.ruleSettings?.status).reason,
        )
        assertTrue(controller.backToSummary())
        assertEquals(HistoryBrowsePage.SUMMARY, controller.state.value.page)
    }

    /** 返回摘要後的遲到規則設定回應不得重新切換頁面或寫入狀態。 */
    @Test
    fun `late rule settings response after back is discarded`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail("match"))
        runCurrent()
        assertTrue(controller.showRuleSettings())
        advanceTimeBy(250)
        runCurrent()
        assertTrue(controller.backToSummary())
        transport.respondRuleSettings()
        runCurrent()
        assertEquals(HistoryBrowsePage.SUMMARY, controller.state.value.page)
        assertEquals(null, controller.state.value.ruleSettings)
    }

    /** 規則設定只於點擊後查詢；返回與再次開啟使用同場快取並保留摘要位置。 */
    @Test
    fun `test rule settings loads lazily and reuses same match cache`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail("match"))
        runCurrent()
        assertTrue(transport.ruleSettingsRequests.isEmpty())
        assertTrue(controller.rememberSummaryPosition(31.5))
        assertTrue(controller.showRuleSettings())
        advanceTimeBy(250)
        runCurrent()
        val config = ruleSettingsConfig()
        transport.respondRuleSettings(config)
        runCurrent()
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.ruleSettings?.status)
        assertTrue(controller.backToSummary())
        assertEquals(31.5, controller.state.value.summary?.scrollOffset)
        assertTrue(controller.showRuleSettings())
        runCurrent()
        assertEquals(1, transport.ruleSettingsRequests.size)
        assertEquals(config, controller.state.value.ruleSettings?.config)
    }

    /** 牌面狀態頁會補查同場規則設定，且不重複排程或離開目前頁面。 */
    @Test
    fun `round state ensures same match rule settings without navigation`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        val matchId = "00000000-0000-0000-0000-000000000001"
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary(matchId)))
        runCurrent()
        assertTrue(controller.showSummary(matchId))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(HistoryMatchDetailDto(summary(matchId), listOf(HistoryRoundSummaryDto(1, null, null))))
        runCurrent()
        assertTrue(controller.showRound(1))
        advanceTimeBy(250)
        runCurrent()
        transport.respondRoundEvents(matchId)
        runCurrent()
        assertTrue(controller.showRoundState(HistoryRoundPositionDto.Initial))
        advanceTimeBy(250)
        runCurrent()
        transport.respondRoundState(matchId)
        runCurrent()

        assertFalse(controller.ensureRoundStateRuleSettings())
        advanceTimeBy(250)
        runCurrent()
        assertTrue(controller.ensureRoundStateRuleSettings())
        assertEquals(HistoryBrowsePage.ROUND_STATE, controller.state.value.page)
        assertEquals(HistoryBrowseStatus.Loading, controller.state.value.ruleSettings?.status)
        assertFalse(controller.ensureRoundStateRuleSettings())
        advanceTimeBy(250)
        runCurrent()
        assertEquals(1, transport.ruleSettingsRequests.size)
    }

    /** 規則設定失敗保留手動重試，冷卻期間不能重送。 */
    @Test
    fun `test rule settings retry waits for cooldown and preserves selected match`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail("match"))
        runCurrent()
        assertTrue(controller.showRuleSettings())
        advanceTimeBy(250)
        runCurrent()
        transport.respondRuleSettings()
        runCurrent()
        assertFalse(controller.canRetry())
        assertTrue(controller.retry())
        runCurrent()
        assertEquals(1, transport.ruleSettingsRequests.size)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, transport.ruleSettingsRequests.size)
        assertEquals("match", transport.ruleSettingsRequests.last().matchId)
        transport.respondRuleSettings(ruleSettingsConfig())
        runCurrent()
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.ruleSettings?.status)
        assertFalse(controller.canRetry())
    }

    /** 規則設定等待逾時後取消該要求，不會清除成功摘要。 */
    @Test
    fun `test rule settings timeout can retry without losing summary`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail("match"))
        runCurrent()
        assertTrue(controller.showRuleSettings())
        advanceTimeBy(250)
        runCurrent()
        val requestId = transport.ruleSettingsRequests.single().requestId
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(requestId in transport.cancelledIds)
        assertEquals(HistoryBrowseStatus.Failed(HistoryBrowseFailure.CLIENT_TIMEOUT), controller.state.value.ruleSettings?.status)
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.summary?.status)
        assertTrue(controller.retry())
        runCurrent()
        assertEquals(2, transport.ruleSettingsRequests.size)
    }

    /** 查詢條件更新會清除仍屬舊對局的規則設定狀態。 */
    @Test
    fun `query reset removes rule settings state`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        transport.respondList(entries = listOf(summary("match")))
        runCurrent()
        assertTrue(controller.showSummary("match"))
        advanceTimeBy(250)
        runCurrent()
        transport.respondSummary(detail("match"))
        runCurrent()
        assertTrue(controller.showRuleSettings())
        assertTrue(controller.state.value.ruleSettings != null)
        assertTrue(controller.updateQuery(controller.state.value.query.copy(sortDirection = HistorySortDirectionDto.ASC)))
        assertEquals(null, controller.state.value.ruleSettings)
    }

    /** client 等待十秒後會取消要求並可透過重試重新送出。 */
    @Test
    fun `test timeout cancels targeted request and retry sends again`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        val timedOut = transport.listRequests.single().requestId
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(timedOut), transport.cancelledIds)
        assertEquals(HistoryBrowseStatus.Failed(HistoryBrowseFailure.CLIENT_TIMEOUT), controller.state.value.list.status)
        assertTrue(controller.retry())
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, transport.listRequests.size)
    }

    /** 傳送失敗會標示 SEND_FAILED 並保留可重試意圖。 */
    @Test
    fun `test send failed is retryable`() = runTest {
        val transport = FakeHistoryQueryTransport(sendFailed = true)
        val controller = controller(transport)
        controller.open()
        runCurrent()
        assertEquals(HistoryBrowseStatus.Failed(HistoryBrowseFailure.SEND_FAILED), controller.state.value.list.status)
        transport.sendFailed = false
        assertTrue(controller.retry())
        advanceTimeBy(250)
        runCurrent()
        transport.respondList(entries = listOf(summary("retried")))
        runCurrent()
        assertEquals(HistoryBrowseStatus.Ready, controller.state.value.list.status)
    }

    /** session 清除會關閉瀏覽、清空內容並丟棄遲到回應。 */
    @Test
    fun `test session clear closes and discards content`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        val requestId = transport.listRequests.single().requestId
        transport.sessionRevision.value = 1L
        runCurrent()
        assertTrue(controller.state.value.closed)
        assertEquals(HistoryBrowseFailure.DISCONNECTED, assertIs<HistoryBrowseStatus.Failed>(controller.state.value.list.status).reason)
        transport.emit(ClientHistoryQueryState.ListResult(HistoryListResponseDto(requestId, listOf(summary("late")))))
        runCurrent()
        assertTrue(controller.state.value.list.entries.isEmpty())
    }

    /** 關閉瀏覽後取消工作，遲到回應不得重新寫入狀態。 */
    @Test
    fun `test close rejects late response`() = runTest {
        val transport = FakeHistoryQueryTransport()
        val controller = controller(transport)
        controller.open()
        runCurrent()
        val requestId = transport.listRequests.single().requestId
        controller.close()
        transport.emit(ClientHistoryQueryState.ListResult(HistoryListResponseDto(requestId, listOf(summary("late")))))
        runCurrent()
        assertTrue(controller.state.value.closed)
        assertTrue(controller.state.value.list.entries.isEmpty())
    }

    /** 以測試作用域建立使用虛擬單調時間的控制器。
     *
     * @param transport 模擬查詢傳輸。
     * @return 綁定目前測試作用域的歷史瀏覽控制器。
     */
    private fun TestScope.controller(transport: FakeHistoryQueryTransport): HistoryBrowseController = HistoryBrowseController(transport, backgroundScope, now = { testScheduler.currentTime.milliseconds })

    /** 建立最小可用的歷史摘要。 */
    private fun summary(matchId: String): HistoryMatchSummaryDto = HistoryMatchSummaryDto(
        matchId = matchId,
        ruleId = null,
        startedAtEpochMillis = null,
        endedAtEpochMillis = null,
        outcome = HistoryOutcomeFilterDto.COMPLETED,
        integrity = HistoryIntegrityFilterDto.COMPLETE,
        participants = emptyList<HistoryParticipantSummaryDto>(),
        roundCount = 0,
        resultsAvailable = false,
    )

    /** 建立指定對局的摘要回覆內容。 */
    private fun detail(matchId: String): HistoryMatchDetailDto = HistoryMatchDetailDto(summary(matchId), emptyList())

    /** 建立經正式 registry 編碼的規則設定回覆。
     * @return 可用於驗證規則設定快取的 DTO。
     */
    private fun ruleSettingsConfig(): GameConfigDto = GameConfig(RiichiRuleConfig()).toDto(
        DefaultNetworkDtoRegistries().apply { registerBundledNetworkDtos() },
    )

    /** 以 MutableStateFlow 模擬 client 歷史查詢傳輸。
     *
     * @property sendFailed 是否讓下一次清單要求產生傳送失敗狀態。
     */
    private class FakeHistoryQueryTransport(
        var sendFailed: Boolean = false,
    ) : HistoryQueryTransport {
        /** 傳輸目前可觀察的結果。 */
        private val mutableState = MutableStateFlow<ClientHistoryQueryState>(ClientHistoryQueryState.Idle)

        /** 傳輸目前的 session 世代。 */
        override val sessionRevision = MutableStateFlow(0L)

        /** 模擬伺服器公布的預設查詢冷卻。 */
        override val minimumInterval = MutableStateFlow(250.milliseconds)

        /** 可觀察的查詢結果。 */
        override val state: StateFlow<ClientHistoryQueryState> = mutableState.asStateFlow()

        /** 已發出的清單要求。 */
        val listRequests = mutableListOf<HistoryListRequestDto>()

        /** 已發出的摘要要求。 */
        val summaryRequests = mutableListOf<HistorySummaryRequestDto>()

        /** 已發出的規則設定要求。 */
        val ruleSettingsRequests = mutableListOf<HistoryRuleSettingsRequestDto>()

        /** 已取消的要求識別碼。 */
        val cancelledIds = mutableListOf<String>()

        /** 發出清單要求並回報配對結果。 */
        override fun queryList(request: HistoryListRequestDto): String {
            val actual = request.copy(requestId = "list-${listRequests.size + 1}")
            listRequests += actual
            if (sendFailed) mutableState.value = ClientHistoryQueryState.SendFailed(actual.requestId)
            return actual.requestId
        }

        /** 發出摘要要求並回報配對結果。 */
        override fun querySummary(request: HistorySummaryRequestDto): String {
            val actual = request.copy(requestId = "summary-${summaryRequests.size + 1}")
            summaryRequests += actual
            mutableState.value = ClientHistoryQueryState.Loading(actual.requestId)
            return actual.requestId
        }

        /** 發出規則設定要求並回報配對結果。 */
        override fun queryRuleSettings(request: HistoryRuleSettingsRequestDto): String {
            val actual = request.copy(requestId = "rules-${ruleSettingsRequests.size + 1}")
            ruleSettingsRequests += actual
            mutableState.value = ClientHistoryQueryState.Loading(actual.requestId)
            return actual.requestId
        }

        /** 已發出的事件頁要求。 */
        val roundEventsRequests = mutableListOf<HistoryRoundEventsRequestDto>()

        /** 發出事件頁查詢並記錄要求。
         * @param request 單局事件要求。
         * @return 配對識別碼。
         */
        override fun queryRoundEvents(request: HistoryRoundEventsRequestDto): String {
            val actual = request.copy(requestId = "events-${roundEventsRequests.size + 1}")
            roundEventsRequests += actual
            mutableState.value = ClientHistoryQueryState.Loading(actual.requestId)
            return actual.requestId
        }

        /** 已發出的牌面要求。 */
        val roundStateRequests = mutableListOf<HistoryRoundStateRequestDto>()

        /** 發出牌面查詢並記錄要求。
         * @param request 單局牌面要求。
         * @return 配對識別碼。
         */
        override fun queryRoundState(request: HistoryRoundStateRequestDto): String {
            val actual = request.copy(requestId = "state-${roundStateRequests.size + 1}")
            roundStateRequests += actual
            mutableState.value = ClientHistoryQueryState.Loading(actual.requestId)
            return actual.requestId
        }

        /** 記錄控制器取消的要求。 */
        override fun cancel(requestId: String): Boolean {
            cancelledIds += requestId
            return true
        }

        /** 更新模擬傳輸的可觀察結果。
         *
         * @param result 新的查詢結果。
         */
        fun emit(result: ClientHistoryQueryState) {
            mutableState.value = result
        }

        /** 發出清單回應。
         *
         * @param requestId 回應所配對的要求識別碼。
         * @param entries 回應中的清單摘要。
         * @param nextCursor 下一頁游標。
         * @param errorCode 回應中的穩定錯誤碼。
         * @param allowAll 伺服器目前允許全部紀錄查詢。
         */
        fun respondList(
            requestId: String = listRequests.last().requestId,
            entries: List<HistoryMatchSummaryDto> = emptyList(),
            nextCursor: String? = null,
            errorCode: HistoryQueryErrorCodeDto? = null,
            allowAll: Boolean = false,
        ) {
            mutableState.value = ClientHistoryQueryState.ListResult(HistoryListResponseDto(requestId, entries, nextCursor, errorCode, allowAll))
        }

        /** 發出摘要回應。
         *
         * @param detail 回應中的摘要內容，可為 null。
         * @param requestId 回應所配對的要求識別碼。
         */
        fun respondSummary(detail: HistoryMatchDetailDto?, requestId: String = summaryRequests.last().requestId) {
            mutableState.value = ClientHistoryQueryState.SummaryResult(HistorySummaryResponseDto(requestId, detail))
        }

        /** 回覆最小有效事件頁。
         * @param matchId 對局識別碼。
         * @param requestId 要配對的要求識別碼。
         */
        fun respondRoundEvents(matchId: String, requestId: String = roundEventsRequests.last().requestId) {
            val identity = HistoryReplayIdentityDto(
                matchId,
                "00000000-0000-0000-0000-000000000002",
                listOf(HistoryReplayPlayerIdentityDto(0, null, null)),
            )
            mutableState.value = ClientHistoryQueryState.RoundEventsResult(
                HistoryRoundEventsResponseDto(
                    requestId,
                    matchId,
                    1,
                    0,
                    HistoryRoundEventsDto(identity, 1, emptyList(), null, emptyList()),
                ),
            )
        }

        /** 回覆最小有效初始牌面。
         * @param matchId 對局識別碼。
         * @param requestId 要配對的要求識別碼。
         */
        fun respondRoundState(matchId: String, requestId: String = roundStateRequests.last().requestId) {
            val identity = HistoryReplayIdentityDto(
                matchId,
                "00000000-0000-0000-0000-000000000002",
                listOf(HistoryReplayPlayerIdentityDto(0, null, null)),
            )
            val state = HistoryRoundStateDto(
                identity = identity,
                roundNumber = 1,
                position = HistoryRoundPositionDto.Initial,
                tileCatalog = emptyList(),
                players = listOf(HistoryReplayPlayerStateDto(0, emptyList(), emptyList(), null, emptyList(), 0, WindDto.EAST, null)),
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
            mutableState.value = ClientHistoryQueryState.RoundStateResult(
                HistoryRoundStateResponseDto(requestId, matchId, 1, HistoryRoundPositionDto.Initial, state),
            )
        }

        /** 發出規則設定回應。
         * @param config 回應中的完整遊戲設定，可為 null。
         * @param requestId 回應所配對的要求識別碼。
         */
        fun respondRuleSettings(config: GameConfigDto? = null, requestId: String = ruleSettingsRequests.last().requestId) {
            mutableState.value = ClientHistoryQueryState.RuleSettingsResult(HistoryRuleSettingsResponseDto(requestId, config))
        }
    }
}
