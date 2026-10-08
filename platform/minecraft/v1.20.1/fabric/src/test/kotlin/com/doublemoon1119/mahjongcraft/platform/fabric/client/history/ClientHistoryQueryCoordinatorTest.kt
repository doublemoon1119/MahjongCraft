package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryFiltersDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortFieldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證客戶端歷史查詢傳輸邊界與工作階段生命週期。 */
class ClientHistoryQueryCoordinatorTest {
    /** 清單要求會產生識別碼、進入載入狀態並交給傳送器。 */
    @Test
    fun `test list query sends request and accepts response`() {
        val sender = FakeHistoryQuerySender()
        val coordinator = ClientHistoryQueryCoordinator(sender, ClientHistoryQuerySettings())

        val requestId = coordinator.queryList(HistoryListRequestDto("caller-id", scope = HistoryQueryScopeDto.OWN, sortField = HistorySortFieldDto.ENDED_AT, sortDirection = HistorySortDirectionDto.DESC, filters = HistoryQueryFiltersDto.NONE, pageSize = 20, cursor = null))

        assertEquals(requestId, sender.listRequest?.requestId)
        assertIs<ClientHistoryQueryState.Loading>(coordinator.state.value)
        coordinator.applyList(HistoryListResponseDto(requestId, emptyList(), nextCursor = null, errorCode = null, allowAll = false))
        assertIs<ClientHistoryQueryState.ListResult>(coordinator.state.value)
    }

    /** 傳送例外會保留要求識別碼於明確的送出失敗狀態。 */
    @Test
    fun `test send failure identifies failed request`() {
        val sender = FakeHistoryQuerySender(shouldFail = true)
        val coordinator = ClientHistoryQueryCoordinator(sender, ClientHistoryQuerySettings())

        val requestId = coordinator.querySummary(HistorySummaryRequestDto("caller-id", "match-id", scope = HistoryQueryScopeDto.OWN))

        assertEquals(ClientHistoryQueryState.SendFailed(requestId), coordinator.state.value)
        assertFalse(coordinator.cancel(requestId))
    }

    /** 取消相符要求後回到閒置狀態，且不推進工作階段版本。 */
    @Test
    fun `test matching cancellation returns idle without session change`() {
        val sender = FakeHistoryQuerySender()
        val coordinator = ClientHistoryQueryCoordinator(sender, ClientHistoryQuerySettings())
        val requestId = coordinator.queryList(HistoryListRequestDto("caller-id", scope = HistoryQueryScopeDto.OWN, sortField = HistorySortFieldDto.ENDED_AT, sortDirection = HistorySortDirectionDto.DESC, filters = HistoryQueryFiltersDto.NONE, pageSize = 20, cursor = null))
        val revision = coordinator.sessionRevision.value

        assertFalse(coordinator.cancel("different-id"))
        assertTrue(coordinator.cancel(requestId))
        assertEquals(ClientHistoryQueryState.Idle, coordinator.state.value)
        assertEquals(revision, coordinator.sessionRevision.value)
    }

    /** 種類不符的回應不會消耗要求，正確種類仍可完成。 */
    @Test
    fun `test wrong response kind does not consume pending request`() {
        val sender = FakeHistoryQuerySender()
        val coordinator = ClientHistoryQueryCoordinator(sender, ClientHistoryQuerySettings())
        val requestId = coordinator.queryList(HistoryListRequestDto("caller-id", scope = HistoryQueryScopeDto.OWN, sortField = HistorySortFieldDto.ENDED_AT, sortDirection = HistorySortDirectionDto.DESC, filters = HistoryQueryFiltersDto.NONE, pageSize = 20, cursor = null))

        coordinator.applySummary(HistorySummaryResponseDto(requestId, detail = null, errorCode = null))
        assertIs<ClientHistoryQueryState.Loading>(coordinator.state.value)
        coordinator.applyList(HistoryListResponseDto(requestId, emptyList(), nextCursor = null, errorCode = null, allowAll = false))
        assertIs<ClientHistoryQueryState.ListResult>(coordinator.state.value)
    }

    /** 規則設定要求會產生識別碼並只接受同種類回應。 */
    @Test
    fun `test rule settings query correlates response kind`() {
        val sender = FakeHistoryQuerySender()
        val coordinator = ClientHistoryQueryCoordinator(sender, ClientHistoryQuerySettings())
        val requestId = coordinator.queryRuleSettings(HistoryRuleSettingsRequestDto("caller-id", "match-id", scope = HistoryQueryScopeDto.OWN))

        assertEquals(requestId, sender.ruleSettingsRequest?.requestId)
        coordinator.applyRuleSettings(HistoryRuleSettingsResponseDto("unrelated", config = null, errorCode = null))
        assertIs<ClientHistoryQueryState.Loading>(coordinator.state.value)
        coordinator.applySummary(HistorySummaryResponseDto(requestId, detail = null, errorCode = null))
        assertIs<ClientHistoryQueryState.Loading>(coordinator.state.value)
        coordinator.applyRuleSettings(HistoryRuleSettingsResponseDto(requestId, config = null, errorCode = null))
        assertIs<ClientHistoryQueryState.RuleSettingsResult>(coordinator.state.value)
    }

    /** 單局事件與桌況要求均使用獨立種類並接受對應回覆。 */
    @Test
    fun `test round query kinds correlate responses`() {
        val sender = FakeHistoryQuerySender()
        val coordinator = ClientHistoryQueryCoordinator(sender, ClientHistoryQuerySettings())
        val eventsId = coordinator.queryRoundEvents(HistoryRoundEventsRequestDto("caller", "match", roundNumber = 1, scope = HistoryQueryScopeDto.OWN, startTransactionIndex = 0, limit = 20))
        assertEquals(eventsId, sender.roundEventsRequest?.requestId)
        coordinator.applyRoundState(HistoryRoundStateResponseDto("wrong", "match", 1, HistoryRoundPositionDto.Initial, state = null, errorCode = null))
        assertIs<ClientHistoryQueryState.Loading>(coordinator.state.value)
        coordinator.applyRoundEvents(HistoryRoundEventsResponseDto(eventsId, "other-match", 1, 0, events = null, errorCode = null))
        assertIs<ClientHistoryQueryState.Loading>(coordinator.state.value)
        coordinator.applyRoundEvents(HistoryRoundEventsResponseDto(eventsId, "match", 1, 0, events = null, errorCode = null))
        assertIs<ClientHistoryQueryState.RoundEventsResult>(coordinator.state.value)

        val stateId = coordinator.queryRoundState(HistoryRoundStateRequestDto("caller", "match", roundNumber = 1, scope = HistoryQueryScopeDto.OWN, position = HistoryRoundPositionDto.Initial))
        assertEquals(stateId, sender.roundStateRequest?.requestId)
        coordinator.applyRoundState(HistoryRoundStateResponseDto(stateId, "match", 1, HistoryRoundPositionDto.Initial, state = null, errorCode = null))
        assertIs<ClientHistoryQueryState.RoundStateResult>(coordinator.state.value)
    }

    /** 斷線清理後不接受原連線的規則設定回覆。 */
    @Test
    fun `test rule settings response is discarded after session clear`() {
        val coordinator = ClientHistoryQueryCoordinator(FakeHistoryQuerySender(), ClientHistoryQuerySettings())
        val requestId = coordinator.queryRuleSettings(HistoryRuleSettingsRequestDto("caller-id", "match-id", scope = HistoryQueryScopeDto.OWN))
        coordinator.clear()
        coordinator.applyRuleSettings(HistoryRuleSettingsResponseDto(requestId, config = null, errorCode = null))
        assertEquals(ClientHistoryQueryState.Idle, coordinator.state.value)
    }

    /** 清除會推進工作階段版本、回到閒置並拒絕舊回應。 */
    @Test
    fun `test clear advances session and rejects stale response`() {
        val sender = FakeHistoryQuerySender()
        val coordinator = ClientHistoryQueryCoordinator(sender, ClientHistoryQuerySettings())
        val requestId = coordinator.queryList(HistoryListRequestDto("caller-id", scope = HistoryQueryScopeDto.OWN, sortField = HistorySortFieldDto.ENDED_AT, sortDirection = HistorySortDirectionDto.DESC, filters = HistoryQueryFiltersDto.NONE, pageSize = 20, cursor = null))
        val previousRevision = coordinator.sessionRevision.value

        coordinator.clear()

        assertEquals(previousRevision + 1, coordinator.sessionRevision.value)
        assertEquals(ClientHistoryQueryState.Idle, coordinator.state.value)
        coordinator.applyList(HistoryListResponseDto(requestId, emptyList(), nextCursor = null, errorCode = null, allowAll = false))
        assertEquals(ClientHistoryQueryState.Idle, coordinator.state.value)
    }

    /**
     * 測試用的歷史查詢傳送器，可選擇模擬傳送例外。
     *
     * @property shouldFail 是否在傳送時拋出模擬例外。
     */
    private class FakeHistoryQuerySender(
        private val shouldFail: Boolean = false,
    ) : HistoryQuerySender {
        /** 最近一次送出的清單要求。 */
        var listRequest: HistoryListRequestDto? = null

        /** 最近一次送出的摘要要求。 */
        var summaryRequest: HistorySummaryRequestDto? = null

        /** 最近一次送出的規則設定要求。 */
        var ruleSettingsRequest: HistoryRuleSettingsRequestDto? = null

        /** 最近一次送出的單局事件要求。 */
        var roundEventsRequest: HistoryRoundEventsRequestDto? = null

        /** 最近一次送出的單局牌面要求。 */
        var roundStateRequest: HistoryRoundStateRequestDto? = null

        /** 保存清單要求或模擬傳送失敗。 */
        override fun sendList(request: HistoryListRequestDto) {
            if (shouldFail) throw IllegalStateException("simulated send failure")
            listRequest = request
        }

        /** 保存摘要要求或模擬傳送失敗。 */
        override fun sendSummary(request: HistorySummaryRequestDto) {
            if (shouldFail) throw IllegalStateException("simulated send failure")
            summaryRequest = request
        }

        /** 保存規則設定要求或模擬傳送失敗。 */
        override fun sendRuleSettings(request: HistoryRuleSettingsRequestDto) {
            if (shouldFail) throw IllegalStateException("simulated send failure")
            ruleSettingsRequest = request
        }

        /** 保存事件要求或模擬傳送失敗。
         * @param request 單局事件查詢封套。
         */
        override fun sendRoundEvents(request: HistoryRoundEventsRequestDto) {
            if (shouldFail) throw IllegalStateException("simulated send failure")
            roundEventsRequest = request
        }

        /** 保存牌面要求或模擬傳送失敗。
         * @param request 單局牌面查詢封套。
         */
        override fun sendRoundState(request: HistoryRoundStateRequestDto) {
            if (shouldFail) throw IllegalStateException("simulated send failure")
            roundStateRequest = request
        }
    }
}
