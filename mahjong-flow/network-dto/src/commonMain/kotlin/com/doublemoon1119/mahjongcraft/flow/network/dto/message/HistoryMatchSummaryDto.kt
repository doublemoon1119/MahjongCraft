package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史清單中的單筆對局摘要。
 *
 *
 * @property matchId 對局識別碼。
 * @property ruleId 使用的規則模組識別碼。
 * @property startedAtEpochMillis 對局開始 UTC epoch milliseconds。
 * @property endedAtEpochMillis 對局結束 UTC epoch milliseconds。
 * @property outcome 對局結果；未知時為 null。
 * @property integrity 歷史資料的完整性狀態。
 * @property integrityDiagnostic 完整性不足時的穩定診斷摘要。
 * @property durationMillis 對局持續時間（毫秒）。
 * @property participants 參與者清單。
 * @property roundCount 可用的回合數。
 * @property resultsAvailable 是否保存完整 Replay；單局查閱仍須驗證內容與 codec，不限制安全摘要查詢。
 * @property results 可取得的終局結果。
 */
@Serializable
data class HistoryMatchSummaryDto(
    val matchId: String,
    val ruleId: String?,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val outcome: HistoryOutcomeFilterDto?,
    val integrity: HistoryIntegrityFilterDto,
    val integrityDiagnostic: String?,
    val durationMillis: Long?,
    val participants: List<HistoryParticipantSummaryDto>,
    val roundCount: Int?,
    val resultsAvailable: Boolean,
    val results: List<HistoryResultSummaryDto>,
)
