package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryLimits
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證回放一局時向伺服器要資料的順序。 */
class HistoryReplayRoundTest {
    /** 不知道交易總數時先要第一頁事件，知道後從記住的步數要牌面。 */
    @Test
    fun `an unknown count reads the first event page before states`() {
        val round = HistoryReplayRound(roundNumber = 1, initialStep = 5, transactionCount = null)

        val first = round.nextRequest()
        round.markRequested(HistoryReplayFetch.Events(0))
        assertNull(round.nextRequest())
        round.acceptEvents(start = 0, events = events(start = 0, next = PAGE, count = TRANSACTION_COUNT))

        assertEquals(HistoryReplayFetch.Events(0), first)
        assertEquals(TRANSACTION_COUNT + 1, round.stepCount)
        assertEquals(5, round.step)
        assertEquals(HistoryReplayFetch.States(HistoryReplayRequest(5, HistoryQueryLimits.MAX_ROUND_STATES)), round.nextRequest())
    }

    /** 目前這一步有牌面後先讀完整局事件，再預先要後面的牌面。 */
    @Test
    fun `remaining event pages come before prefetching states`() {
        val round = HistoryReplayRound(roundNumber = 1, initialStep = 0, transactionCount = TRANSACTION_COUNT)
        val states = assertIs(round.nextRequest())
        round.markRequested(states)
        round.acceptStates(start = 0, states = (0 until states.request.count).map { state() })

        assertEquals(HistoryReplayFetch.Events(0), round.nextRequest())
        round.markRequested(HistoryReplayFetch.Events(0))
        round.acceptEvents(start = 0, events = events(start = 0, next = PAGE, count = TRANSACTION_COUNT))
        assertEquals(HistoryReplayFetch.Events(PAGE), round.nextRequest())
        round.markRequested(HistoryReplayFetch.Events(PAGE))
        round.acceptEvents(start = PAGE, events = events(start = PAGE, next = null, count = TRANSACTION_COUNT))

        assertTrue(round.eventsComplete)
        val ahead = HistoryReplayRequest(HistoryQueryLimits.MAX_ROUND_STATES, HistoryQueryLimits.MAX_ROUND_STATES)
        assertEquals(HistoryReplayFetch.States(ahead), round.nextRequest())
    }

    /** 不接續的事件頁會被丟掉，失敗後可以重新要同一頁。 */
    @Test
    fun `out of order event pages are ignored and failures can retry`() {
        val round = HistoryReplayRound(roundNumber = 1, initialStep = 0, transactionCount = null)
        round.markRequested(HistoryReplayFetch.Events(0))
        round.acceptEvents(start = PAGE, events = events(start = PAGE, next = null, count = TRANSACTION_COUNT))
        assertFalse(round.eventsComplete)
        assertNull(round.stepCount)

        round.markRequested(HistoryReplayFetch.Events(0))
        round.releaseRequest()
        assertEquals(HistoryReplayFetch.Events(0), round.nextRequest())
    }

    /**
     * 取得要求並確認是一段牌面。
     *
     * @param fetch 要求。
     * @return 牌面要求。
     */
    private fun assertIs(fetch: HistoryReplayFetch?): HistoryReplayFetch.States = kotlin.test.assertIs<HistoryReplayFetch.States>(fetch)

    /**
     * 建立一頁事件。
     *
     * @param start 第一筆交易索引。
     * @param next 下一頁的第一筆交易索引；最後一頁為 null。
     * @param count 本局交易總數。
     * @return 事件頁。
     */
    private fun events(start: Int, next: Int?, count: Int): HistoryRoundEventsDto = HistoryRoundEventsDto(
        identity = IDENTITY,
        roundNumber = 1,
        transactions = (start until (next ?: count)).map { HistoryReplayTransactionDto(it, 0, isOpening = it == 0, declaredTileCountAfter = 0, facts = emptyList()) },
        nextTransactionIndex = next,
        transactionCount = count,
        tileCatalog = emptyList(),
    )

    /**
     * 建立一份最小牌面；這裡只看步數，不看內容。
     *
     * @return 牌面。
     */
    private fun state(): HistoryRoundStateDto = HistoryRoundStateDto(
        identity = IDENTITY,
        roundNumber = 1,
        position = HistoryReplayBuffer.positionOf(0),
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

    /** 測試共用的數值。 */
    private companion object {
        /** 本局交易總數。 */
        const val TRANSACTION_COUNT = 40

        /** 事件頁大小。 */
        const val PAGE = 20

        /** 測試共用的對局身分。 */
        val IDENTITY = HistoryReplayIdentityDto(matchId = "00000000-0000-0000-0000-000000000001", venueId = "00000000-0000-0000-0000-000000000002", players = emptyList())
    }
}
