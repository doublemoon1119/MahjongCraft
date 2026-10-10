package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史清單的複合篩選條件。
 *
 * @property ruleId 規則模組的 namespaced ID；null 表示不限制規則。
 * @property outcome 對局結果篩選；null 表示不限制結果。
 * @property integrity 對局完整性篩選；null 表示不限制完整性。
 * @property ai AI 座位篩選；null 表示不限制 AI。
 * @property endedAtFromEpochMillis 結束時間的 UTC 下界，包含此時間。
 * @property endedAtBeforeEpochMillis 結束時間的 UTC 上界，不包含此時間。
 * @property ownRankMin 查詢玩家可接受的最小名次。
 * @property ownRankMax 查詢玩家可接受的最大名次。
 * @property playerName 參與者名稱的大小寫不敏感部分比對文字。
 * @property matchId 指定對局 UUID 文字；留空表示不限制對局。
 */
@Serializable
data class HistoryQueryFiltersDto(
    val ruleId: String?,
    val outcome: HistoryOutcomeFilterDto?,
    val integrity: HistoryIntegrityFilterDto?,
    val ai: HistoryAiFilterDto?,
    val endedAtFromEpochMillis: Long?,
    val endedAtBeforeEpochMillis: Long?,
    val ownRankMin: Int?,
    val ownRankMax: Int?,
    val playerName: String?,
    val matchId: String?,
) {
    companion object {
        /** 不套用任何篩選條件。 */
        val NONE: HistoryQueryFiltersDto = HistoryQueryFiltersDto(
            ruleId = null,
            outcome = null,
            integrity = null,
            ai = null,
            endedAtFromEpochMillis = null,
            endedAtBeforeEpochMillis = null,
            ownRankMin = null,
            ownRankMax = null,
            playerName = null,
            matchId = null,
        )
    }
}
