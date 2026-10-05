package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryError
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryErrorCode
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.DEFAULT_REPLAY_JSON_BYTES
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadError
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadResult
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

/** 正式歷史查詢允許讀入的 Replay UTF-8 位元組上限。 */
internal const val HISTORY_REPLAY_QUERY_BYTES: Int = DEFAULT_REPLAY_JSON_BYTES

/**
 * 在 writer 管理 lease 內先驗證公開條件，再讀取指定 Replay；不保存解析快取。
 *
 * @param T 中立歷史讀模型型別。
 * @param database 唯一 session 所屬資料庫。
 * @param access 可信連線身分。
 * @param policy 目前有效查詢政策。
 * @param matchId 欲查閱的對局識別碼。
 * @param scope 原要求的查詢範圍。
 * @param excluded 活動或轉移中不可公開場次。
 * @param parse 受限 JSON 解析入口。
 * @param read 只重建指定局與位置的有界讀取器。
 * @param identity 讀模型的對局及參與者身分。
 * @return 完整且與 metadata 一致的資料，或不揭露內部內容的錯誤。
 */
internal suspend fun <T> readAuthorizedHistoryRound(
    database: SqliteHistoryDatabase,
    access: HistoryQueryAccess,
    policy: HistoryQueryPolicy,
    matchId: Uuid,
    scope: HistoryQueryScope,
    excluded: Set<String>,
    parse: suspend (String) -> ReplayReadResult<JsonObject>,
    read: suspend (JsonObject) -> ReplayReadResult<T>,
    identity: (T) -> HistoryReplayIdentity,
): HistoryQueryResult<T> {
    val authorized = authorizedHistoryReplay(database, access, policy, matchId, scope, excluded)
    if (authorized is HistoryQueryResult.Failure) return authorized
    val metadata = (authorized as HistoryQueryResult.Success).value
    val payload = when (val result = database.readReplayPayload(matchId.toString(), HISTORY_REPLAY_QUERY_BYTES)) {
        HistoryReplayPayloadRead.Missing -> return historyRoundFailure(HistoryQueryErrorCode.NOT_AVAILABLE)
        HistoryReplayPayloadRead.TooLarge -> return historyRoundFailure(HistoryQueryErrorCode.CONTENT_TOO_LARGE)
        is HistoryReplayPayloadRead.Found -> result.payload
    }
    val document = when (val parsed = parse(payload)) {
        is ReplayReadResult.Failure -> return parsed.error.toQueryFailure()
        is ReplayReadResult.Success -> parsed.value
    }
    return when (val result = read(document)) {
        is ReplayReadResult.Failure -> result.error.toQueryFailure()
        is ReplayReadResult.Success -> {
            val actual = identity(result.value)
            val players = actual.players.map { Triple(it.initialSeatIndex, it.playerId.toString(), it.aiStrategyKey) }
            val expected = metadata.participants.map { Triple(it.seatIndex, it.playerId, it.aiStrategyId) }
            if (actual.matchId != matchId || actual.venueId.toString() != metadata.tableId || players != expected) {
                historyRoundFailure(HistoryQueryErrorCode.NOT_AVAILABLE)
            } else {
                HistoryQueryResult.Success(result.value)
            }
        }
    }
}

/**
 * 以目前政策、SQL 參與者與終端證據確認 Replay 可公開；不讀入內容。
 *
 * @param database 唯一 session 所屬資料庫。
 * @param access 可信連線身分。
 * @param policy 目前有效查詢政策。
 * @param matchId 欲查閱的對局識別碼。
 * @param scope 原查詢範圍。
 * @param excluded 仍在活動或轉移中的對局。
 * @return 可公開 metadata，或穩定安全錯誤。
 */
internal fun authorizedHistoryReplay(
    database: SqliteHistoryDatabase,
    access: HistoryQueryAccess,
    policy: HistoryQueryPolicy,
    matchId: Uuid,
    scope: HistoryQueryScope,
    excluded: Set<String>,
): HistoryQueryResult<SqliteHistoryQueryEntry> {
    if (!policy.queryEnabled) return historyRoundFailure(HistoryQueryErrorCode.QUERY_DISABLED)
    if (scope == HistoryQueryScope.ALL && (!access.isAdministrator || !policy.allowAdministratorQuery)) {
        return historyRoundFailure(HistoryQueryErrorCode.ACCESS_DENIED)
    }
    val query = HistoryListRequest(scope = scope, pageSize = 1).toSqliteQuery(access, excluded).copy(matchId = matchId.toString())
    val entry = database.readHistoryQueryPage(query).entries.singleOrNull()
        ?: return historyRoundFailure(HistoryQueryErrorCode.NOT_AVAILABLE)
    val evidence = database.readArchiveStatusEvidence(matchId.toString())
    return if (entry.state != HistoryStoredMatchState.COMPLETED || entry.endedAtEpochMillis == null || !evidence.saved || evidence.pruned || evidence.failed) {
        historyRoundFailure(HistoryQueryErrorCode.NOT_AVAILABLE)
    } else {
        HistoryQueryResult.Success(entry)
    }
}

/**
 * 將讀取器分類轉為既有查詢錯誤，不洩漏 codec 或文件內容。
 * @return 安全查詢失敗。
 */
private fun ReplayReadError.toQueryFailure(): HistoryQueryResult.Failure = historyRoundFailure(
    when (this) {
        ReplayReadError.SELECTION_NOT_FOUND -> HistoryQueryErrorCode.INVALID_REQUEST
        ReplayReadError.LIMIT_EXCEEDED -> HistoryQueryErrorCode.CONTENT_TOO_LARGE
        ReplayReadError.INVALID_DOCUMENT, ReplayReadError.UNSUPPORTED_CONTENT -> HistoryQueryErrorCode.NOT_AVAILABLE
    },
)

/**
 * 建立只含英文內部診斷的錯誤，不回傳 SQL 或原始 payload。
 * @param code 穩定查詢錯誤碼。
 * @return 安全失敗。
 */
internal fun historyRoundFailure(code: HistoryQueryErrorCode): HistoryQueryResult.Failure = HistoryQueryResult.Failure(
    HistoryQueryError(code, "History round query failed: ${code.name}"),
)
