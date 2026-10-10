package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/**
 * 歷史單一贏家詳情。
 *
 * @property seatIndex 贏家座位。
 * @property detailFields 規則專屬詳情欄位。
 * @property hand 保存結算順序的立牌與和牌張；未記錄時為 null。
 */
data class HistoryWinnerDetails(
    val seatIndex: Int,
    val detailFields: List<HistoryWinDetailField>,
    val hand: HistoryReplayWinningHand? = null,
)
