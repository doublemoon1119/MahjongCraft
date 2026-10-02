package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證歷史畫面使用的單場保存狀態不會依缺少的資料猜測為已保存。 */
class HistoryArchiveStatusTest {
    /** 已存在 Replay 時優先回報保存完成。 */
    @Test
    fun `saved evidence reports saved`() {
        assertEquals(
            HistoryArchiveStatusDto.SAVED,
            evidence(saved = true).toDto(HistoryRecordingDecision.RECORDING, pendingOutbox = false, queryEnabled = true, authorized = true, active = false),
        )
    }

    /** 尚有 outbox 事件時回報處理中。 */
    @Test
    fun `pending outbox reports pending`() {
        assertEquals(
            HistoryArchiveStatusDto.PENDING,
            evidence().toDto(HistoryRecordingDecision.RECORDING, pendingOutbox = true, queryEnabled = true, authorized = true, active = false),
        )
    }

    /** 清理墓碑表示內容已不可查詢，不得回報保存完成。 */
    @Test
    fun `pruned evidence reports missing`() {
        assertEquals(
            HistoryArchiveStatusDto.MISSING,
            evidence(pruned = true).toDto(HistoryRecordingDecision.STOPPED_PRUNED, pendingOutbox = false, queryEnabled = true, authorized = true, active = false),
        )
    }

    /** 權限不足時不向呼叫者揭露場次的保存證據。 */
    @Test
    fun `unauthorized status reports denied`() {
        assertEquals(
            HistoryArchiveStatusDto.DENIED,
            evidence(saved = true).toDto(HistoryRecordingDecision.RECORDING, pendingOutbox = false, queryEnabled = true, authorized = false, active = false),
        )
    }

    /** 已確認的記錄停止原因回報失敗，不誤顯示為尚未開始。 */
    @Test
    fun `recording failure reports failed`() {
        assertEquals(
            HistoryArchiveStatusDto.FAILED,
            evidence(failed = true).toDto(HistoryRecordingDecision.STOPPED_TRANSFER_INTERRUPTED, pendingOutbox = false, queryEnabled = true, authorized = true, active = false),
        )
    }

    /** 權威對局仍存在時，即使已有暫存證據也只能回報處理中。 */
    @Test
    fun `active match reports pending`() {
        assertEquals(
            HistoryArchiveStatusDto.PENDING,
            evidence(saved = true).toDto(HistoryRecordingDecision.RECORDING, pendingOutbox = false, queryEnabled = true, authorized = true, active = true),
        )
    }

    /** 已排除的場次即使尚未離開結算狀態，也不誤顯示為等待保存。 */
    @Test
    fun `excluded active match is not pending`() {
        assertEquals(
            HistoryArchiveStatusDto.EXCLUDED,
            evidence().toDto(HistoryRecordingDecision.EXCLUDED_AI, pendingOutbox = false, queryEnabled = true, authorized = true, active = true),
        )
    }

    /**
     * 建立最小保存證據。
     *
     * @param saved 是否已保存完整紀錄。
     * @param pruned 是否已被清理。
     * @param failed 是否有記錄失敗證據。
     * @return 用於狀態判定的測試證據。
     */
    private fun evidence(
        saved: Boolean = false,
        pruned: Boolean = false,
        failed: Boolean = false,
    ): HistoryArchiveStatusEvidence = HistoryArchiveStatusEvidence(
        saved = saved,
        pruned = pruned,
        pendingSqlEvents = false,
        failed = failed,
        participantIds = emptySet(),
    )
}
