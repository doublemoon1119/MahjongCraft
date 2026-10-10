package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 對局摘要及其局級索引資料。
 *
 * @property summary 對局共用摘要。
 * @property rounds 已知的局級摘要。
 */
data class HistoryMatchDetail(
    val summary: HistoryMatchSummary,
    val rounds: List<HistoryRoundSummary>,
)
