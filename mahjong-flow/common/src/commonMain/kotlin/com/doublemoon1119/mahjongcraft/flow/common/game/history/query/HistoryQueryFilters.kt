package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 歷史清單的條件篩選。
 *
 * @property ruleId 規則模組 ID。
 * @property outcome 結束狀態。
 * @property integrity 完整性狀態。
 * @property ai AI 參與狀態。
 * @property endedAtFromEpochMillis 結束時間下界。
 * @property endedAtBeforeEpochMillis 結束時間上界。
 * @property ownRank 目前使用者的終局名次範圍。
 * @property playerName 參與者名稱的大小寫不敏感部分比對文字。
 * @property matchId 指定對局 UUID 的文字篩選；null 表示不限制對局。
 */
data class HistoryQueryFilters(
    val ruleId: String? = null,
    val outcome: HistoryOutcomeFilter? = null,
    val integrity: HistoryIntegrityFilter? = null,
    val ai: HistoryAiFilter? = null,
    val endedAtFromEpochMillis: Long? = null,
    val endedAtBeforeEpochMillis: Long? = null,
    val ownRank: HistoryRankRange? = null,
    val playerName: String? = null,
    val matchId: String? = null,
) {
    init {
        require(playerName == null || playerName.length <= HistoryQueryLimits.MAX_PLAYER_NAME_LENGTH) {
            "History player name filter exceeds its length limit"
        }
        require(matchId == null || matchId.length <= HistoryQueryLimits.MAX_MATCH_ID_LENGTH) {
            "History match ID filter exceeds its length limit"
        }
    }
}
