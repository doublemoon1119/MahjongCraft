package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryResultSummaryDto
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證歷史畫面格式化工具不會用零值偽造未知資料。 */
class HistoryScreenTextTest {
    /** 少於或多於四人時仍保留所有參與者，不推測缺少的名次與分數。 */
    @Test
    fun `participant ordering supports variable seat counts and missing results`() {
        for (count in listOf(2, 5)) {
            val participants = (0 until count).map { HistoryParticipantSummaryDto(it, "player-$it") }.reversed()
            val entry = HistoryMatchSummaryDto(
                matchId = "match", ruleId = null, startedAtEpochMillis = null, endedAtEpochMillis = null,
                outcome = null, integrity = HistoryIntegrityFilterDto.INCOMPLETE, roundCount = null, resultsAvailable = false,
                participants = participants, results = emptyList(),
            )
            assertEquals((0 until count).map { "player-$it" }, HistoryScreenText.rankedParticipants(entry).map { it.playerId })
            assertEquals(null to null, HistoryScreenText.ownResult(entry, "player-0"))
        }
    }

    /** 單局時間缺端點或反向時不偽造零秒。 */
    @Test
    fun `round duration requires both ordered endpoints`() {
        assertEquals("—", HistoryScreenText.intervalDuration(null, 1000))
        assertEquals("—", HistoryScreenText.intervalDuration(0, null))
        assertEquals("—", HistoryScreenText.intervalDuration(1000, 0))
        assertEquals("0m 00s", HistoryScreenText.intervalDuration(1000, 1000))
        assertEquals("1m 01s", HistoryScreenText.intervalDuration(0, 61000))
    }

    /** 起訖時間應以指定時區顯示至秒，不含原始 ISO 分隔或毫秒。 */
    @Test
    fun `end time uses local readable format`() {
        assertEquals("1970-01-01 08:00:00", HistoryScreenText.endedAt(0, ZoneId.of("Asia/Taipei")))
        assertEquals("1970-01-01 08:00:50", HistoryScreenText.endedAt(50000, ZoneId.of("Asia/Taipei")))
    }

    /** 最終名次優先，未知名次放最後且同名次按座位排序。 */
    @Test
    fun `participants use authoritative ranks instead of scores or seats`() {
        val entry = HistoryMatchSummaryDto(
            matchId = "match", ruleId = null, startedAtEpochMillis = null, endedAtEpochMillis = null,
            outcome = null, integrity = HistoryIntegrityFilterDto.COMPLETE, roundCount = null, resultsAvailable = true,
            participants = listOf(HistoryParticipantSummaryDto(0, "unknown"), HistoryParticipantSummaryDto(1, "second"), HistoryParticipantSummaryDto(2, "first"), HistoryParticipantSummaryDto(3, "tie")),
            results = listOf(HistoryResultSummaryDto("unknown", 99999, null), HistoryResultSummaryDto("first", 100, 1), HistoryResultSummaryDto("second", 200, 2), HistoryResultSummaryDto("tie", 300, 2)),
        )
        assertEquals(listOf("first", "second", "tie", "unknown"), HistoryScreenText.rankedParticipants(entry).map { it.playerId })
    }

    /** 未知時長及時間應顯示短橫線。 */
    @Test
    fun `unknown temporal values use placeholders`() {
        assertEquals("—", HistoryScreenText.endedAt(null))
        assertEquals("—", HistoryScreenText.duration(null))
    }

    /** 寬度不足時應裁切並保留省略符號。 */
    @Test
    fun `long text is trimmed`() {
        assertEquals("abc…", HistoryScreenText.trim("abcdef", 4) { it.length })
    }
}
