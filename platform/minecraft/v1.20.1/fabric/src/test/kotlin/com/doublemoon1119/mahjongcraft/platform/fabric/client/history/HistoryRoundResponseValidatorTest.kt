package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayDiscardDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayMeldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailFieldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinnerDetailsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 測試用 canonical 對局 UUID。 */
private const val TEST_MATCH_ID = "00000000-0000-0000-0000-000000000001"

/** 測試用 canonical 牌桌 UUID。 */
private const val TEST_TABLE_ID = "00000000-0000-0000-0000-000000000002"

/** 驗證單局歷史回覆的上下文、座位與牌索引安全邊界。 */
class HistoryRoundResponseValidatorTest {
    /** 正常事件頁可以通過驗證。 */
    @Test
    fun `test valid event response`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val response = HistoryRoundEventsResponseDto(
            requestId = "request",
            matchId = TEST_MATCH_ID,
            roundNumber = 1,
            startTransactionIndex = 0,
            events = events(),
        )

        assertIs<HistoryRoundValidationResult.Success<*>>(HistoryRoundResponseValidator.validateEvents(request, response))
    }

    /** 過期或不同要求的回覆不得通過。 */
    @Test
    fun `test mismatched event request is rejected`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val response = HistoryRoundEventsResponseDto("other", TEST_MATCH_ID, 1, 0, events())

        val result = HistoryRoundResponseValidator.validateEvents(request, response)
        assertEquals(HistoryRoundValidationError.REQUEST_MISMATCH, assertIs<HistoryRoundValidationResult.Invalid>(result).reason)
    }

    /** 錯誤回覆可以保留穩定錯誤碼，但不得混入成功內容。 */
    @Test
    fun `test error response is accepted without content`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val response = HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE)

        assertEquals(
            HistoryQueryErrorCodeDto.NOT_AVAILABLE,
            assertIs<HistoryRoundValidationResult.Error>(HistoryRoundResponseValidator.validateEvents(request, response)).code,
        )
    }

    /** 錯誤回覆攜帶成功內容時拒絕整個回覆。 */
    @Test
    fun `test error response with content is rejected`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val response = HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, events(), HistoryQueryErrorCodeDto.NOT_AVAILABLE)

        assertEquals(HistoryRoundValidationError.CONTENT_MISMATCH, assertIs<HistoryRoundValidationResult.Invalid>(HistoryRoundResponseValidator.validateEvents(request, response)).reason)
    }

    /** 牌索引超過目錄範圍時拒絕事件頁。 */
    @Test
    fun `test invalid event tile index is rejected`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val invalid = events(tileIndex = 2)
        val result = HistoryRoundResponseValidator.validateEvents(request, HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, invalid))

        assertEquals(HistoryRoundValidationError.TILE_INDEX_INVALID, assertIs<HistoryRoundValidationResult.Invalid>(result).reason)
    }

    /** 交易索引不連續時拒絕事件頁。 */
    @Test
    fun `test gapped transaction index is rejected`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val invalid = events().copy(transactions = listOf(events().transactions.single().copy(index = 2)), nextTransactionIndex = 3)
        val result = HistoryRoundResponseValidator.validateEvents(request, HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, invalid))

        assertEquals(HistoryRoundValidationError.TRANSACTION_INDEX_INVALID, assertIs<HistoryRoundValidationResult.Invalid>(result).reason)
    }

    /** 下一頁游標不等於最後索引加一時拒絕事件頁。 */
    @Test
    fun `test gapped next transaction index is rejected`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val invalid = events().copy(nextTransactionIndex = 2)
        val result = HistoryRoundResponseValidator.validateEvents(request, HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, invalid))

        assertEquals(HistoryRoundValidationError.TRANSACTION_INDEX_INVALID, assertIs<HistoryRoundValidationResult.Invalid>(result).reason)
    }

    /** 交易中的牌索引不得超出該交易宣告數量。 */
    @Test
    fun `test fact tile beyond declared count is rejected`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val invalid = events().copy(transactions = listOf(events().transactions.single().copy(declaredTileCountAfter = 0)))
        val result = HistoryRoundResponseValidator.validateEvents(request, HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, invalid))

        assertEquals(HistoryRoundValidationError.TILE_INDEX_INVALID, assertIs<HistoryRoundValidationResult.Invalid>(result).reason)
    }

    /** 結算分數變更只能引用同一結算中的座位與分數欄位。 */
    @Test
    fun `test outcome score changes must be covered by scores`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val outcome = HistoryRoundOutcomeDto("test:win", listOf(0), mapOf(0 to 25000), "WIN", emptyList(), null, scoreChangesBySeat = mapOf(1 to 1000))
        val base = events()
        val invalid = base.copy(transactions = listOf(base.transactions.single().copy(facts = listOf(HistoryReplayFactDto.Completion("round_completed", outcome)))))

        val result = HistoryRoundResponseValidator.validateEvents(request, HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, invalid))
        assertEquals(HistoryRoundValidationError.CONTENT_MISMATCH, assertIs<HistoryRoundValidationResult.Invalid>(result).reason)
    }

    /** 贏家詳情只能屬於唯一受益座位，欄位與模板必須使用 namespaced ID。 */
    @Test
    fun `test winner details require valid beneficiary and identifiers`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val details = listOf(HistoryWinnerDetailsDto(0, "test:template", listOf(HistoryWinDetailFieldDto("invalid", HistoryWinDetailValueDto.Text("test:label")))))
        val outcome = HistoryRoundOutcomeDto("test:win", listOf(0), mapOf(0 to 25000), "WIN", emptyList(), null, winnerDetails = details)
        val base = events()
        val invalid = base.copy(transactions = listOf(base.transactions.single().copy(facts = listOf(HistoryReplayFactDto.Completion("round_completed", outcome)))))

        val result = HistoryRoundResponseValidator.validateEvents(request, HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, invalid))
        assertEquals(HistoryRoundValidationError.CONTENT_MISMATCH, assertIs<HistoryRoundValidationResult.Invalid>(result).reason)
    }

    /** 和牌詳情中的牌參照不得超出交易當下已宣告牌數量。 */
    @Test
    fun `test winner detail tiles respect declared tile count`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val details = listOf(HistoryWinnerDetailsDto(0, "test:template", listOf(HistoryWinDetailFieldDto("test:tiles", HistoryWinDetailValueDto.Tiles(listOf(1))))))
        val outcome = HistoryRoundOutcomeDto("test:win", listOf(0), mapOf(0 to 25000), "WIN", emptyList(), null, winnerDetails = details)
        val base = events().copy(tileCatalog = listOf(TileDto.Numeric(SuitDto.CHARACTER, 1), TileDto.Numeric(SuitDto.CHARACTER, 2)))
        val invalid = base.copy(transactions = listOf(base.transactions.single().copy(facts = listOf(HistoryReplayFactDto.Completion("round_completed", outcome)))))

        val result = HistoryRoundResponseValidator.validateEvents(request, HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, invalid))
        assertEquals(HistoryRoundValidationError.CONTENT_MISMATCH, assertIs<HistoryRoundValidationResult.Invalid>(result).reason)
    }

    /** 多名贏家可保留各自條目尾綴與局部牌參照，且完整資料通過驗證。 */
    @Test
    fun `test rich winner details are accepted`() {
        val request = HistoryRoundEventsRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val identity = identity().copy(
            players = listOf(
                HistoryReplayPlayerIdentityDto(0, null, "test:ai"),
                HistoryReplayPlayerIdentityDto(1, null, "test:ai"),
            ),
        )
        val details = listOf(
            HistoryWinnerDetailsDto(
                0,
                "test:template",
                listOf(
                    HistoryWinDetailFieldDto(
                        "test:entries",
                        HistoryWinDetailValueDto.Entries(
                            listOf(
                                HistoryWinDetailValueDto.Entries.EntryDto(
                                    "test:pattern",
                                    trailingTranslationKey = "test:points",
                                    trailingTranslationArgument = "3",
                                ),
                            ),
                        ),
                    ),
                    HistoryWinDetailFieldDto("test:tiles", HistoryWinDetailValueDto.Tiles(listOf(1))),
                ),
            ),
            HistoryWinnerDetailsDto(
                1,
                "test:template",
                listOf(HistoryWinDetailFieldDto("test:tiles", HistoryWinDetailValueDto.Tiles(listOf(0)))),
            ),
        )
        val outcome = HistoryRoundOutcomeDto("test:win", listOf(0, 1), mapOf(0 to 25000, 1 to 25000), "WIN", emptyList(), null, winnerDetails = details)
        val base = events().copy(
            identity = identity,
            tileCatalog = listOf(TileDto.Numeric(SuitDto.CHARACTER, 1), TileDto.Numeric(SuitDto.CHARACTER, 2)),
            transactions = listOf(events().transactions.single().copy(declaredTileCountAfter = 2, facts = listOf(HistoryReplayFactDto.Completion("round_completed", outcome)))),
        )

        assertIs<HistoryRoundValidationResult.Success<*>>(
            HistoryRoundResponseValidator.validateEvents(request, HistoryRoundEventsResponseDto("request", TEST_MATCH_ID, 1, 0, base)),
        )
    }

    /** 桌況回覆的位置與要求一致時可以通過驗證。 */
    @Test
    fun `test valid state context`() {
        val request = HistoryRoundStateRequestDto("request", TEST_MATCH_ID, roundNumber = 1, position = HistoryRoundPositionDto.Initial)
        val response = HistoryRoundStateResponseDto("request", TEST_MATCH_ID, 1, HistoryRoundPositionDto.Initial, state = state())

        assertIs<HistoryRoundValidationResult.Success<*>>(HistoryRoundResponseValidator.validateState(request, response))
    }

    /** 桌況回覆中的牌索引超出目錄時拒絕。 */
    @Test
    fun `test invalid state tile index is rejected`() {
        val request = HistoryRoundStateRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val response = HistoryRoundStateResponseDto("request", TEST_MATCH_ID, 1, HistoryRoundPositionDto.Initial, state = state(tileIndex = 2))

        assertEquals(HistoryRoundValidationError.TILE_INDEX_INVALID, assertIs<HistoryRoundValidationResult.Invalid>(HistoryRoundResponseValidator.validateState(request, response)).reason)
    }

    /** 桌況身份不是 canonical UUID 時拒絕。 */
    @Test
    fun `test malformed identity uuid is rejected`() {
        val request = HistoryRoundStateRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val response = HistoryRoundStateResponseDto("request", TEST_MATCH_ID, 1, HistoryRoundPositionDto.Initial, state = state().copy(identity = identity().copy(tableId = "table")))

        assertEquals(HistoryRoundValidationError.CONTEXT_MISMATCH, assertIs<HistoryRoundValidationResult.Invalid>(HistoryRoundResponseValidator.validateState(request, response)).reason)
    }

    /** 結算結果引用不存在座位時拒絕桌況。 */
    @Test
    fun `test invalid outcome seat is rejected`() {
        val request = HistoryRoundStateRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val outcome = HistoryRoundOutcomeDto("test:complete", listOf(3), emptyMap(), null, emptyList(), null)
        val response = HistoryRoundStateResponseDto("request", TEST_MATCH_ID, 1, HistoryRoundPositionDto.Initial, state = state().copy(outcome = outcome))

        assertEquals(HistoryRoundValidationError.SEAT_INDEX_INVALID, assertIs<HistoryRoundValidationResult.Invalid>(HistoryRoundResponseValidator.validateState(request, response)).reason)
    }

    /** 桌況回覆位置與要求不一致時拒絕。 */
    @Test
    fun `test state position mismatch is rejected`() {
        val request = HistoryRoundStateRequestDto("request", TEST_MATCH_ID, roundNumber = 1, position = HistoryRoundPositionDto.AfterTransaction(2))
        val response = HistoryRoundStateResponseDto("request", TEST_MATCH_ID, 1, HistoryRoundPositionDto.Initial, state = state())

        assertEquals(HistoryRoundValidationError.POSITION_MISMATCH, assertIs<HistoryRoundValidationResult.Invalid>(HistoryRoundResponseValidator.validateState(request, response)).reason)
    }

    /** 合法副露來源與已取走捨牌可以引用同一個牌索引。 */
    @Test
    fun `test valid meld source and taken discard alias`() {
        val request = HistoryRoundStateRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val player = HistoryReplayPlayerStateDto(
            0,
            emptyList(),
            listOf(HistoryReplayMeldDto(MeldTypeDto.Pon, listOf(0, 0, 0), 0, RelativeDirectionDto.Left)),
            null,
            listOf(HistoryReplayDiscardDto(0, true, emptySet())),
            25000,
            WindDto.EAST,
            null,
        )
        val response = HistoryRoundStateResponseDto("request", TEST_MATCH_ID, 1, HistoryRoundPositionDto.Initial, state = state().copy(players = listOf(player)))

        assertIs<HistoryRoundValidationResult.Success<*>>(HistoryRoundResponseValidator.validateState(request, response))
    }

    /** identity 與桌況玩家座位不一致時拒絕桌況。 */
    @Test
    fun `test repeated identity seat is rejected`() {
        val request = HistoryRoundStateRequestDto("request", TEST_MATCH_ID, roundNumber = 1)
        val duplicateIdentity = identity().copy(players = listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"), HistoryReplayPlayerIdentityDto(0, null, "test:ai")))
        val response = HistoryRoundStateResponseDto("request", TEST_MATCH_ID, 1, HistoryRoundPositionDto.Initial, state = state().copy(identity = duplicateIdentity))

        assertEquals(HistoryRoundValidationError.SEAT_INDEX_INVALID, assertIs<HistoryRoundValidationResult.Invalid>(HistoryRoundResponseValidator.validateState(request, response)).reason)
    }

    /** 建立含一張實體牌及未知安全事實的事件頁。
     * @param tileIndex 事實直接涉及的牌索引。
     * @return 待驗證的事件資料。
     */
    private fun events(tileIndex: Int = 0): HistoryRoundEventsDto = HistoryRoundEventsDto(
        identity = identity(),
        roundNumber = 1,
        transactions = listOf(
            HistoryReplayTransactionDto(
                index = 0,
                occurredAtEpochMillis = 1,
                isOpening = true,
                declaredTileCountAfter = 1,
                facts = listOf(HistoryReplayFactDto.Opaque("test:fact", 0, listOf(tileIndex), emptyList())),
            ),
        ),
        nextTransactionIndex = 1,
        tileCatalog = listOf(TileDto.Numeric(SuitDto.CHARACTER, 1)),
    )

    /** 建立單一 AI 座位的完整初始牌面。
     * @param tileIndex 活牌牆中的牌索引。
     * @return 待驗證的牌面資料。
     */
    private fun state(tileIndex: Int = 0): HistoryRoundStateDto = HistoryRoundStateDto(
        identity = identity(),
        roundNumber = 1,
        position = HistoryRoundPositionDto.Initial,
        tileCatalog = listOf(TileDto.Numeric(SuitDto.CHARACTER, 1)),
        players = listOf(HistoryReplayPlayerStateDto(0, emptyList(), emptyList(), null, emptyList(), 25000, WindDto.EAST, null)),
        wallTiles = listOf(tileIndex),
        reservedTiles = emptyList(),
        currentPlayerSeat = 0,
        dealerSeat = 0,
        prevalentWind = WindDto.EAST,
        roundPosition = MatchRoundPositionDto(0, WindDto.EAST, 1, MatchRoundPhaseDto.REGULAR),
        comboCount = 0,
        finishedPlayerSeats = emptySet(),
        dynamicRuleState = null,
        hasPendingReaction = false,
        hasPendingKanReaction = false,
        outcome = null,
    )

    /** 建立合法 UUID 與單一 AI 座位的身份資料。
     * @return 事件及牌面共用的身份資料。
     */
    private fun identity() = HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai")))
}

/** 驗證單局歷史成功快取的數量、容量、工作階段與期限限制。 */
class HistoryRoundCacheTest {
    /** 超過事件頁數量上限時移除最舊項目。 */
    @Test
    fun `test event entry cap evicts oldest`() {
        val cache = HistoryRoundCache(maxEventEntries = 1)
        val first = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val second = first.copy(startTransactionIndex = 1)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(first, value, 1)
        cache.putEvents(second, value, 1)
        assertNull(cache.getEvents(first))
        assertEquals(value, cache.getEvents(second))
    }

    /** 讀取項目後會更新 LRU 順序，新增項目淘汰未讀取的舊項目。 */
    @Test
    fun `test cache access updates lru order`() {
        val cache = HistoryRoundCache(maxEventEntries = 2)
        val first = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val second = first.copy(startTransactionIndex = 1)
        val third = first.copy(startTransactionIndex = 2)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(first, value, 1)
        cache.putEvents(second, value, 1)
        cache.getEvents(first)
        cache.putEvents(third, value, 1)
        assertEquals(value, cache.getEvents(first))
        assertNull(cache.getEvents(second))
    }

    /** 不同工作階段的結果不能被目前工作階段讀取。 */
    @Test
    fun `test session invalidation removes old entries`() {
        val cache = HistoryRoundCache()
        val key = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(key, value, 1)
        cache.invalidateOtherSessions(2)
        assertNull(cache.getEvents(key))
    }

    /** 超過總 UTF-8 容量的單一項目不加入快取。 */
    @Test
    fun `test oversized entry is rejected`() {
        val cache = HistoryRoundCache(maxBytes = 2)
        val key = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        assertEquals(false, cache.putEvents(key, value, 3))
        assertNull(cache.getEvents(key))
    }

    /** 成功項目超過存活時間後不得再次命中。 */
    @Test
    fun `test expired entry is not returned`() {
        var elapsed = Duration.ZERO
        val cache = HistoryRoundCache(now = { elapsed }, ttl = 30.seconds)
        val key = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val second = key.copy(startTransactionIndex = 1)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(key, value, 1)
        cache.putEvents(second, value, 1)
        elapsed += 31.seconds
        assertNull(cache.getEvents(key))
        assertNull(cache.getEvents(second))
    }

    /** 桌況項目使用獨立數量上限。 */
    @Test
    fun `test state entry cap evicts oldest`() {
        val cache = HistoryRoundCache(maxStateEntries = 1)
        val first = HistoryRoundCacheKey.State(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, HistoryRoundPositionDto.Initial)
        val second = first.copy(position = HistoryRoundPositionDto.AfterTransaction(1))
        val value = cacheState()

        cache.putState(first, value, 1)
        cache.putState(second, value, 1)
        assertNull(cache.getState(first))
        assertEquals(value, cache.getState(second))
    }

    /** 事件頁與桌況共用總容量上限，超額時淘汰最舊項目。 */
    @Test
    fun `test combined cache byte budget evicts oldest`() {
        val cache = HistoryRoundCache(maxBytes = 3)
        val eventKey = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val stateKey = HistoryRoundCacheKey.State(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, HistoryRoundPositionDto.Initial)
        val event = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(eventKey, event, 2)
        cache.putState(stateKey, cacheState(), 2)
        assertNull(cache.getEvents(eventKey))
        assertEquals(cacheState(), cache.getState(stateKey))
    }

    /** 快取鍵的範圍、局序號與位置均會隔離結果。 */
    @Test
    fun `test cache key context is isolated`() {
        val cache = HistoryRoundCache()
        val key = HistoryRoundCacheKey.State(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, HistoryRoundPositionDto.Initial)
        val other = key.copy(roundNumber = 2, scope = HistoryQueryScopeDto.ALL, position = HistoryRoundPositionDto.AfterTransaction(1))
        cache.putState(key, cacheState(), 1)

        assertEquals(cacheState(), cache.getState(key))
        assertNull(cache.getState(other))
    }

    /** 移除一場對局時不影響其他對局。 */
    @Test
    fun `test evict match preserves another match`() {
        val cache = HistoryRoundCache()
        val removed = HistoryRoundCacheKey.State(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, HistoryRoundPositionDto.Initial)
        val retained = removed.copy(matchId = "00000000-0000-0000-0000-000000000003")
        cache.putState(removed, cacheState(), 1)
        cache.putState(retained, cacheState(), 1)

        cache.evictMatch(TEST_MATCH_ID)
        assertNull(cache.getState(removed))
        assertEquals(cacheState(), cache.getState(retained))
    }

    /** 建立快取測試用最小合法桌況。 */
    private fun cacheState(): HistoryRoundStateDto = HistoryRoundStateDto(
        identity = HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))),
        roundNumber = 1,
        position = HistoryRoundPositionDto.Initial,
        tileCatalog = listOf(TileDto.Numeric(SuitDto.CHARACTER, 1)),
        players = listOf(HistoryReplayPlayerStateDto(0, emptyList(), emptyList(), null, emptyList(), 25000, WindDto.EAST, null)),
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
        hasPendingKanReaction = false,
        outcome = null,
    )
}
