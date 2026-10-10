package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 權威歷史待寫事件的持久化快照；此格式與 SQLite schema 分開演進。
 *
 * @property formatVersion 此快照格式的版本號，目前只接受 [FORMAT_VERSION]。
 * @property nextSequenceByMatchId 各對局下一個可分配的事件序號，鍵為對局 UUID 字串。
 * @property pendingEvents 尚未交給歷史儲存端的事件；各場次的事件依序號排序。
 * @property firstMissingSequenceByMatchId 各對局第一個無法完整還原的事件序號，鍵為對局 UUID 字串。
 * @property decisionsByMatchId 各對局固定的歷史記錄決策，值為列舉名稱。
 * @property terminalByMatchId 已不可接續場次的權威終點證據。
 * @property transfersByMatchId 未完成的隔離權威歷史轉移；載入後保留部分紀錄而不重新執行來源。
 */
@Serializable
data class HistoryRecordingPersistenceDto(
    val formatVersion: Int,
    val nextSequenceByMatchId: Map<String, Long>,
    val pendingEvents: List<HistoryOutboxEventPersistenceDto>,
    val firstMissingSequenceByMatchId: Map<String, Long>,
    val decisionsByMatchId: Map<String, String>,
    val terminalByMatchId: Map<String, HistoryRecordingTerminalPersistenceDto>,
    val transfersByMatchId: Map<String, HistoryRecordingTransferPersistenceDto>,
) {
    init {
        require(formatVersion == FORMAT_VERSION) { "Unsupported history outbox format $formatVersion" }
    }

    companion object {
        /** 目前的待寫事件快照格式版本。 */
        const val FORMAT_VERSION: Int = 1

        /** 沒有任何待寫事件、決策與轉移的空快照。 */
        val EMPTY: HistoryRecordingPersistenceDto = HistoryRecordingPersistenceDto(
            formatVersion = FORMAT_VERSION,
            nextSequenceByMatchId = emptyMap(),
            pendingEvents = emptyList(),
            firstMissingSequenceByMatchId = emptyMap(),
            decisionsByMatchId = emptyMap(),
            terminalByMatchId = emptyMap(),
            transfersByMatchId = emptyMap(),
        )
    }
}
