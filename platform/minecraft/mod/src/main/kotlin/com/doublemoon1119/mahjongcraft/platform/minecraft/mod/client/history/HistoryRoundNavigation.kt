package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto

/**
 * 單一歷史局的要求與確認位置。
 *
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
 * @property lastTransactionIndex 已明確確認的最後交易索引。
 * @property transactionCount 伺服器回報的本局交易總數；尚未取得事件頁時為 null。
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
    val lastTransactionIndex: Int? = null,
    val transactionCount: Int? = null,
) {
    /**
     * 判斷交易是否存在：索引小於伺服器回報的交易總數。
     *
     * @param index 交易索引。
     * @return 存在時為 true；尚未取得總數時為 false。
     */
    fun hasTransaction(index: Int): Boolean = transactionCount?.let { index in 0 until it } == true
}

/** 歷史局導航的純資料操作；不替呼叫端推測不存在的交易。 */
internal object HistoryRoundNavigation {
    /**
     * 記錄已通過驗證的事件頁與本局交易總數。
     *
     * @param current 目前局狀態。
     * @param events 已驗證事件頁。
     * @return 更新事件資料與交易索引的局狀態。
     */
    fun recordEvents(current: HistoryBrowseRoundState, events: HistoryRoundEventsDto): HistoryBrowseRoundState = current.copy(
        events = events,
        eventsStatus = HistoryBrowseStatus.Ready,
        nextTransactionIndex = events.nextTransactionIndex,
        lastTransactionIndex = (events.transactionCount - 1).takeIf { it >= 0 },
        transactionCount = events.transactionCount,
    )

    /**
     * 依本局交易總數取得相鄰桌況位置。
     *
     * @param round 目前局狀態。
     * @param position 目前桌況位置。
     * @param direction -1 表示上一筆，1 表示下一筆。
     * @return 已知的相鄰位置，或資料不足時為 null。
     * @throws IllegalArgumentException [direction] 不是 -1 或 1 時拋出。
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
        if (!round.hasTransaction(candidate)) return null
        return HistoryRoundPositionDto.AfterTransaction(candidate)
    }

    /**
     * 取得跳到最後一頁時的事件頁起點堆疊：從 0 開始每頁固定筆數，讓之後往前翻頁仍逐頁回到第一頁。
     *
     * @param transactionCount 本局交易總數。
     * @param pageSize 每頁交易數。
     * @return 由第一頁到最後一頁的起點；沒有交易時只有第一頁。
     */
    fun lastPageStarts(transactionCount: Int, pageSize: Int = EVENTS_PAGE_SIZE): List<Int> {
        require(pageSize > 0) { "Page size must be positive" }
        val lastStart = if (transactionCount <= 0) 0 else (transactionCount - 1) / pageSize * pageSize
        return (0..lastStart step pageSize).toList()
    }

    /** 每個事件頁要求的交易數。 */
    const val EVENTS_PAGE_SIZE: Int = 20
}
