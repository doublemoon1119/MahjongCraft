package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryLimits
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證回放牌面暫存的要求順序、範圍與在途限制。 */
class HistoryReplayBufferTest {
    /** 從開頭播放時先要目前這一步起的一段，收到後接著要後面的步數。 */
    @Test
    fun `requests start at the current step and continue ahead`() {
        val buffer = HistoryReplayBuffer(stepCount = 40)

        val first = assertNotNull(buffer.nextRequest())
        buffer.markRequested(first)
        assertNull(buffer.nextRequest())
        buffer.accept(first.start, states(first))
        val second = assertNotNull(buffer.nextRequest())

        assertEquals(HistoryReplayRequest(0, HistoryQueryLimits.MAX_ROUND_STATES), first)
        assertEquals(HistoryReplayRequest(HistoryQueryLimits.MAX_ROUND_STATES, HistoryQueryLimits.MAX_ROUND_STATES), second)
        assertNotNull(buffer.stateAt(0))
    }

    /** 要求不超過這一局的最後一步，也不超過目前之後保留的範圍。 */
    @Test
    fun `requests stop at the round end and the window`() {
        val short = HistoryReplayBuffer(stepCount = 5)
        val narrow = HistoryReplayBuffer(stepCount = 100, ahead = 3)

        assertEquals(HistoryReplayRequest(0, 5), short.nextRequest())
        assertEquals(HistoryReplayRequest(0, 4), narrow.nextRequest())
    }

    /** 跳到後面時先補目前這一步，往前的步數最後才補，範圍外的牌面會丟掉。 */
    @Test
    fun `jumping fills the current step first and drops steps outside the window`() {
        val buffer = HistoryReplayBuffer(stepCount = 100, behind = 2, ahead = 3)
        val first = assertNotNull(buffer.nextRequest())
        buffer.markRequested(first)
        buffer.accept(first.start, states(first))

        buffer.moveTo(50)
        val forward = assertNotNull(buffer.nextRequest())
        buffer.markRequested(forward)
        buffer.accept(forward.start, states(forward))
        val backward = assertNotNull(buffer.nextRequest())

        assertNull(buffer.stateAt(0))
        assertEquals(HistoryReplayRequest(50, 4), forward)
        assertEquals(HistoryReplayRequest(48, 2), backward)
    }

    /** 失敗後可以重新要求同一段。 */
    @Test
    fun `failed request can be retried`() {
        val buffer = HistoryReplayBuffer(stepCount = 10)
        val request = assertNotNull(buffer.nextRequest())
        buffer.markRequested(request)
        buffer.releaseRequest()

        assertEquals(request, buffer.nextRequest())
    }

    /** 步數 0 是初始牌面，步數 k 是第 k - 1 筆交易之後。 */
    @Test
    fun `steps map to round positions`() {
        assertEquals(HistoryRoundPositionDto.Initial, HistoryReplayBuffer.positionOf(0))
        assertEquals(HistoryRoundPositionDto.AfterTransaction(4), HistoryReplayBuffer.positionOf(5))
    }

    /**
     * 為一段要求產生對應位置的牌面。
     *
     * @param request 要求。
     * @return 依步數排列的牌面。
     */
    private fun states(request: HistoryReplayRequest): List<HistoryRoundStateDto> = (request.start until request.start + request.count).map { step ->
        HistoryRoundStateDto(
            identity = HistoryReplayIdentityDto(matchId = MATCH_ID, venueId = MATCH_ID, players = emptyList()),
            roundNumber = 1,
            position = HistoryReplayBuffer.positionOf(step),
            tileCatalog = emptyList(),
            players = emptyList(),
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
    }

    /** 測試共用的對局識別碼。 */
    private companion object {
        /** 測試共用的對局識別碼。 */
        val MATCH_ID: String = Uuid.random().toString()
    }
}
