package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * 歷史清單與摘要共用的對局中繼資料。
 *
 * @property matchId 對局 UUID。
 * @property ruleId 規則模組 ID。
 * @property startedAtEpochMillis 開始時間。
 * @property endedAtEpochMillis 結束時間。
 * @property duration 對局持續時間。
 * @property outcome 結束狀態。
 * @property integrity 完整性狀態。
 * @property integrityDiagnostic 完整性診斷摘要。
 * @property resultsAvailable 是否保存完整重播資料；不代表已驗證其內容可解碼。
 * @property participants 參與者摘要。
 * @property roundCount 已知的完成局數。
 * @property results 可取得的終局結果。
 */
data class HistoryMatchSummary(
    val matchId: Uuid,
    val ruleId: String?,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val duration: Duration?,
    val outcome: HistoryOutcomeFilter?,
    val integrity: HistoryIntegrityFilter,
    val integrityDiagnostic: String? = null,
    val resultsAvailable: Boolean,
    val participants: List<HistoryParticipantSummary>,
    val roundCount: Int?,
    val results: List<HistoryResultSummary>,
)
