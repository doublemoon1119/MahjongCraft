package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import kotlin.uuid.Uuid

/**
 * 單次伺服器權威狀態交易的結果。
 *
 * @param T 呼叫端自訂的交易結果型別。
 * @property state 交易完成後的完整狀態。
 * @property result 回傳給呼叫端的結果。
 * @property historyDraftsByVenueId 與本次狀態變更一起提交的權威事實；提交後一律交給接收者登記，是否寫入歷史由記錄政策決定。
 * @property historyRecordingFailures 歷史事件記錄失敗的場地 ID；提交狀態時為對應場次記錄序號缺口。
 */
data class AuthoritativeStateUpdate<T>(
    val state: AuthoritativeStateSnapshot,
    val result: T,
    val historyDraftsByVenueId: Map<Uuid, List<HistoryEventDraft>> = emptyMap(),
    val historyRecordingFailures: Set<Uuid> = emptySet(),
)
