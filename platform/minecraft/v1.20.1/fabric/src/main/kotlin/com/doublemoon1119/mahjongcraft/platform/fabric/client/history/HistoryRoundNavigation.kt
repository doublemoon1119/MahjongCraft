package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto

/** 單一歷史局的要求與確認位置。
 * @property roundNumber 局數。
 * @property requestedPosition 最近一次要求的位置。
 * @property confirmedPosition 最近一次成功確認的位置。
 * @property nextTransactionIndex 伺服器明確回傳的下一筆交易索引。
 * @property events 最近成功取得的事件頁。
 * @property state 最近成功取得的桌況。
 * @property eventsStatus 事件頁查詢狀態。
 * @property stateStatus 桌況查詢狀態。
 * @property eventStartIndices 已明確取得的事件頁起點。
 * @property eventPageNumber 最近成功事件頁頁碼。
 * @property eventScrollOffset 事件頁捲動位置。
 * @property stateScrollOffset 桌況捲動位置。
 * @property knownTransactionIndices 已驗證事件頁中的交易索引。
 * @property lastTransactionIndex 已明確確認的最後交易索引。
 */
internal data class HistoryBrowseRoundState(
    val roundNumber: Int,
    val requestedPosition: HistoryRoundPositionDto = HistoryRoundPositionDto.Initial,
    val confirmedPosition: HistoryRoundPositionDto? = null,
    val nextTransactionIndex: Int? = null,
    val events: HistoryRoundEventsDto? = null,
    val state: HistoryRoundStateDto? = null,
    val eventsStatus: HistoryBrowseStatus = HistoryBrowseStatus.Idle,
    val stateStatus: HistoryBrowseStatus = HistoryBrowseStatus.Idle,
    val eventStartIndices: List<Int> = listOf(0),
    val eventPageNumber: Int = 1,
    val eventScrollOffset: Double = 0.0,
    val stateScrollOffset: Double = 0.0,
    val knownTransactionIndices: Set<Int> = emptySet(),
    val lastTransactionIndex: Int? = null,
)

/** 歷史局導航的純資料操作；不替呼叫端推測不存在的交易。 */
internal object HistoryRoundNavigation {
    /**
     * 記錄已通過驗證的事件頁與可跨頁游標。
     * @param current 目前局狀態。
     * @param events 已驗證事件頁。
     * @return 更新事件資料與交易索引的局狀態。
     */
    fun recordEvents(current: HistoryBrowseRoundState, events: HistoryRoundEventsDto): HistoryBrowseRoundState {
        val indices = events.transactions.map { it.index }.toSet()
        val terminal = events.nextTransactionIndex == null
        return current.copy(
            events = events,
            eventsStatus = HistoryBrowseStatus.Ready,
            nextTransactionIndex = events.nextTransactionIndex,
            knownTransactionIndices = current.knownTransactionIndices + indices,
            lastTransactionIndex = if (terminal) events.transactions.maxOfOrNull { it.index } else current.lastTransactionIndex,
        )
    }

    /**
     * 依已知交易索引取得相鄰桌況位置。
     * @param round 目前局狀態。
     * @param position 目前桌況位置。
     * @param direction -1 表示上一筆，1 表示下一筆。
     * @return 已知的相鄰位置，或資料不足時為 null。
     */
    fun nextPosition(round: HistoryBrowseRoundState, position: HistoryRoundPositionDto, direction: Int): HistoryRoundPositionDto? {
        require(direction == -1 || direction == 1) { "Direction must be -1 or 1" }
        val index = when (position) {
            HistoryRoundPositionDto.Initial -> if (direction > 0) -1 else return null
            is HistoryRoundPositionDto.AfterTransaction -> position.index
        }
        if (direction < 0 && index == 0) return HistoryRoundPositionDto.Initial
        if (direction > 0 && index == Int.MAX_VALUE) return null
        val candidate = index + direction
        if (candidate < 0 || candidate !in round.knownTransactionIndices) return null
        return HistoryRoundPositionDto.AfterTransaction(candidate)
    }
}
