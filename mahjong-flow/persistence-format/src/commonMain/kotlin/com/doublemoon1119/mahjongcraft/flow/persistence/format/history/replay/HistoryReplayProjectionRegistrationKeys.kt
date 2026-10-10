package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

/**
 * 歷史投影註冊表目前各分類的穩定登記 key。
 *
 * @property discard 必要牌河轉換器的 key。
 * @property fact 額外語意事實轉換器的 key。
 * @property action 擴充動作轉換器的 key。
 * @property optionalRule 可選規則公開資訊轉換器的 key。
 * @property setAsideTiles 移出手牌的牌轉換器的 key。
 */
data class HistoryReplayProjectionRegistrationKeys(
    val discard: Set<String>,
    val fact: Set<String>,
    val action: Set<String>,
    val optionalRule: Set<String>,
    val setAsideTiles: Set<String>,
)
