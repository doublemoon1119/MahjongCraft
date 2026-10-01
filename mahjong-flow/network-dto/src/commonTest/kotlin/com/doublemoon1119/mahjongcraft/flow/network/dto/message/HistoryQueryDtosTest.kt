package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** 歷史查詢 DTO 的穩定 JSON 編碼測試。 */
class HistoryQueryDtosTest {
    /** 查詢請求應保留篩選、排序、分頁與 cursor。 */
    @Test
    fun `history list request round trips all query fields`() {
        val expected = HistoryListRequestDto(
            requestId = "request-1",
            scope = HistoryQueryScopeDto.ALL,
            sortField = HistorySortFieldDto.OWN_SCORE,
            sortDirection = HistorySortDirectionDto.ASC,
            filters = HistoryQueryFiltersDto(
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
                    participants = listOf(HistoryParticipantSummaryDto(0, "player-1")),
                    roundCount = 8,
                    resultsAvailable = true,
                ),
            ),
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
}
