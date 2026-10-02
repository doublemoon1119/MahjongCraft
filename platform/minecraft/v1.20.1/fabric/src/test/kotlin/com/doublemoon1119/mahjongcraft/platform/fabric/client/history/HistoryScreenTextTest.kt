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
    /** 結束時間應以指定時區顯示至分鐘，不含原始 ISO 分隔或毫秒。 */
    @Test
    fun `end time uses local readable format`() {
        assertEquals("1970-01-01 08:00", HistoryScreenText.endedAt(0, ZoneId.of("Asia/Taipei")))
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
