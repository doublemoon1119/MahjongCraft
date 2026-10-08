package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 歷史查詢 DTO 的穩定 JSON 編碼測試。 */
class HistoryQueryDtosTest {
    /** 單局事件請求保留範圍、局序號與有界頁參數。 */
    @Test
    fun `history round events request round trips bounded selector`() {
        val expected = HistoryRoundEventsRequestDto(
            requestId = "round-events",
            matchId = Uuid.random().toString(),
            scope = HistoryQueryScopeDto.ALL,
            roundNumber = 3,
            startTransactionIndex = 12,
            limit = 20,
        )
        val encoded = expected.encode(Json)
        assertEquals(expected, Json.decodeFromString(HistoryRoundEventsRequestDto.serializer(), encoded))
        assertEquals(expected, encoded.decodeHistoryRoundEventsRequest(Json))
    }

    /** 單局桌況請求的兩種局內位置均使用穩定名稱。 */
    @Test
    fun `history round state request round trips positions`() {
        val matchId = Uuid.random().toString()
        val positions = listOf(
            HistoryRoundPositionDto.Initial,
            HistoryRoundPositionDto.AfterTransaction(7),
        )
        positions.forEach { position ->
            val expected = HistoryRoundStateRequestDto("round-state", matchId, roundNumber = 2, position = position, scope = HistoryQueryScopeDto.OWN)
            val encoded = expected.encode(Json)
            assertEquals(expected, Json.decodeFromString(HistoryRoundStateRequestDto.serializer(), encoded))
            assertEquals(expected, encoded.decodeHistoryRoundStateRequest(Json))
        }
    }

    /** 事件所有變體均應使用明確的 subtype 名稱往返。 */
    @Test
    fun `history replay facts round trip all variants`() {
        val facts = listOf<HistoryReplayFactDto>(
            HistoryReplayFactDto.KnownAction("a", 0, "discard", listOf(1), listOf(2), null),
            HistoryReplayFactDto.Reaction("r", "pon", 1),
            HistoryReplayFactDto.Preparation("p", "deal", 0, "draw"),
            HistoryReplayFactDto.Completion("c", null),
            HistoryReplayFactDto.RuleEffect("e", "mahjongcraft:effect", null),
            HistoryReplayFactDto.Opaque("x", null, emptyList(), listOf(3)),
        )
        val encoded = Json.encodeToString(ListSerializerHolder.serializer(), ListSerializerHolder(facts))
        assertEquals(facts, Json.decodeFromString(ListSerializerHolder.serializer(), encoded).facts)
    }

    /** 桌況回覆應保留 extension 牌種、牌索引與完整局內位置。 */
    @Test
    fun `history round state response round trips extension tile`() {
        val expected = HistoryRoundStateResponseDto(
            requestId = "round-state-response",
            matchId = Uuid.random().toString(),
            roundNumber = 1,
            position = HistoryRoundPositionDto.AfterTransaction(4),
            state = HistoryRoundStateDto(
                identity = HistoryReplayIdentityDto(
                    matchId = Uuid.random().toString(),
                    venueId = Uuid.random().toString(),
                    players = listOf(HistoryReplayPlayerIdentityDto(0, Uuid.random().toString(), null)),
                ),
                roundNumber = 1,
                position = HistoryRoundPositionDto.AfterTransaction(4),
                tileCatalog = listOf(TileDto.Extension("custom:flower")),
                players = emptyList(),
                wallTiles = listOf(0),
                reservedTiles = listOf(1),
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
            ),
            errorCode = null,
        )
        val encoded = Json.encodeToString(HistoryRoundStateResponseDto.serializer(), expected)
        assertEquals(expected, Json.decodeFromString(HistoryRoundStateResponseDto.serializer(), encoded))
        assertTrue("custom:flower" in encoded)
    }

    /** 序列化測試使用的事實集合。
     * @property facts 安全歷史事實 DTO。
     */
    @Serializable
    private data class ListSerializerHolder(val facts: List<HistoryReplayFactDto>)

    /** 查詢請求應保留篩選、排序、分頁與 cursor。 */
    @Test
    fun `history list request round trips all query fields`() {
        val expected = HistoryListRequestDto(
            requestId = "request-1",
            scope = HistoryQueryScopeDto.ALL,
            sortField = HistorySortFieldDto.OWN_SCORE,
            sortDirection = HistorySortDirectionDto.ASC,
            filters = HistoryQueryFiltersDto.NONE.copy(
                ruleId = "mahjongcraft:riichi",
                outcome = HistoryOutcomeFilterDto.COMPLETED,
                integrity = HistoryIntegrityFilterDto.COMPLETE,
                ai = HistoryAiFilterDto.NO_AI,
                endedAtFromEpochMillis = 10L,
                endedAtBeforeEpochMillis = 20L,
                ownRankMin = 1,
                ownRankMax = 3,
            ),
            pageSize = 50,
            cursor = "cursor-1",
        )

        val encoded = Json.encodeToString(HistoryListRequestDto.serializer(), expected)

        assertEquals(expected, Json.decodeFromString(HistoryListRequestDto.serializer(), encoded))
        assertEquals("own_score", Json.parseToJsonElement(encoded).jsonObject["sortField"]?.toString()?.trim('"'))
    }

    /** 回覆只包含安全摘要，不應出現手牌、牌山或事件 payload 欄位。 */
    @Test
    fun `history response does not contain replay content`() {
        val response = HistoryListResponseDto(
            requestId = "request-1",
            entries = listOf(
                HistoryMatchSummaryDto(
                    matchId = "match-1",
                    ruleId = "mahjongcraft:riichi",
                    startedAtEpochMillis = 1L,
                    endedAtEpochMillis = 2L,
                    outcome = HistoryOutcomeFilterDto.COMPLETED,
                    integrity = HistoryIntegrityFilterDto.COMPLETE,
                    participants = listOf(HistoryParticipantSummaryDto(0, "player-1", aiStrategyId = null)),
                    roundCount = 8,
                    resultsAvailable = true,
                    integrityDiagnostic = null,
                    durationMillis = null,
                    results = emptyList(),
                ),
            ),
            nextCursor = null,
            errorCode = null,
            allowAll = false,
            allowStressTest = false,
        )

        val encoded = Json.encodeToString(HistoryListResponseDto.serializer(), response)

        assertFalse("hands" in encoded)
        assertFalse("wall" in encoded)
        assertFalse("payload" in encoded)
    }

    /** 不透明 cursor 的格式錯誤或未知 enum 值不得被接受。 */
    @Test
    fun `history cursor rejects malformed and unknown values`() {
        assertFailsWith<Exception> {
            "not-json".decodeHistoryCursor(Json)
        }
        assertFailsWith<Exception> {
            Json.decodeFromString(
                HistoryListRequestDto.serializer(),
                """{"requestId":"r","sortField":"unknown"}""",
            )
        }
    }

    /** 規則設定查詢請求保留識別碼、對局 UUID 與查詢範圍。 */
    @Test
    fun `history rule settings request round trips and maps to domain`() {
        val expected = HistoryRuleSettingsRequestDto(
            requestId = "request-rules",
            matchId = Uuid.random().toString(),
            scope = HistoryQueryScopeDto.ALL,
        )
        val encoded = expected.encode(Json)
        val decoded = encoded.decodeHistoryRuleSettingsRequest(Json)

        assertEquals(expected, decoded)
        assertEquals(expected.matchId, decoded.toDomain().matchId.toString())
        assertEquals(HistoryQueryScopeDto.ALL.toDomain(), decoded.toDomain().scope)
    }

    /** 不合法對局 UUID 不得由規則設定查詢 mapping 靜默接受。 */
    @Test
    fun `history rule settings request rejects malformed match id`() {
        val request = HistoryRuleSettingsRequestDto("request-rules", "not-a-uuid", scope = HistoryQueryScopeDto.OWN)
        assertFailsWith<IllegalArgumentException> { request.toDomain() }
    }

    /** 規則設定查詢回覆的可選設定與錯誤欄位可透過 JSON 往返。 */
    @Test
    fun `history rule settings response round trips nullable fields`() {
        val expected = HistoryRuleSettingsResponseDto(
            requestId = "request-rules",
            errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE,
            config = null,
        )
        val encoded = expected.encode(Json)

        assertEquals(expected, encoded.decodeHistoryRuleSettingsResponse(Json))
    }
}
