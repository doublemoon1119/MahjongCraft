package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryPruningConfirmation
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import kotlin.uuid.Uuid

/**
 * 一筆尚未封存的權威歷史事件。
 *
 * @property matchId 整場對局的穩定識別碼。
 * @property sequence 該場內由權威 store 指派的單調序號。
 * @property roundNumber 事件發生時的局數。
 * @property occurredAtEpochMillis 事件的 UTC 毫秒時間戳；不決定事件順序。
 * @property payloadVersion 原始事件 DTO 的格式版本。
 * @property payload 原始事件的版本化 JSON；只保存在伺服器側。
 */
internal data class PendingHistoryRecord(
    val matchId: String,
    val sequence: Long,
    val roundNumber: Int,
    val occurredAtEpochMillis: Long,
    val payloadVersion: Int,
    val payload: String,
)

/**
 * 封存時由完整權威事件建立、與 Replay 一起提交的對局摘要。
 *
 * @property matchId 對局 ID。
 * @property tableId 牌桌 ID。
 * @property ruleId 開局規則模組 ID。
 * @property dimensionId 可證實的牌桌維度；位置登記已不存在時為 null。
 * @property startedAtEpochMillis 開局時間。
 * @property endedAtEpochMillis 終局時間。
 * @property participants 開局座位、玩家 ID 與 AI 策略 key。
 * @property rounds 各局起訖時間。
 * @property replayPayload 已完成並驗證的精簡 Replay JSON。
 * @property participantResults 可取得的最終分數與名次；無法取得時保持空集合。
 */
internal data class HistoryArchiveRecord(
    val matchId: String,
    val tableId: String,
    val ruleId: String,
    val dimensionId: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val participants: List<HistoryParticipantRecord>,
    val rounds: List<HistoryRoundRecord>,
    val replayPayload: String,
    val participantResults: List<HistoryParticipantResultRecord> = emptyList(),
)

/**
 * 單一參與者的封存結果投影。
 *
 * @property seatIndex 參與者的開局座位。
 * @property finalScore 最終分數；規則未提供時為 null。
 * @property finalRank 最終名次；無法依規則比較時為 null。
 */
internal data class HistoryParticipantResultRecord(
    val seatIndex: Int,
    val finalScore: Int?,
    val finalRank: Int?,
)

/**
 * 對局開始時的參與者摘要。
 *
 * @property seatIndex 開局座位。
 * @property playerId 玩家 ID。
 * @property aiStrategyId AI 策略 key；真人玩家為 null。
 */
internal data class HistoryParticipantRecord(val seatIndex: Int, val playerId: String, val aiStrategyId: String?)

/**
 * 每局的起訖時間摘要。
 *
 * @property roundNumber 整場實際開局的連續序號，從 1 開始；連莊亦各占一個序號，不等同規則局號。
 * @property startedAtEpochMillis 開局時間。
 * @property endedAtEpochMillis 結束時間；未正常結束時為 null。
 */
internal data class HistoryRoundRecord(val roundNumber: Int, val startedAtEpochMillis: Long, val endedAtEpochMillis: Long?)

/** 受大小限制的 Replay 讀取結果。 */
internal sealed interface HistoryReplayPayloadRead {
    /** 查無已完成 Replay。 */
    data object Missing : HistoryReplayPayloadRead

    /** Replay 超過查詢允許的解析大小。 */
    data object TooLarge : HistoryReplayPayloadRead

    /** 已讀取且尚未解碼的 Replay JSON。
     *
     * @property payload 通過大小限制的 Replay JSON 文字。
     */
    data class Found(val payload: String) : HistoryReplayPayloadRead
}

/**
 * 權威狀態移除對局時寫入的終端摘要，不以載入時缺少對局推測中止。
 *
 * @property matchId 已結束對局的穩定識別碼。
 * @property tableId 原本所屬牌桌的識別碼。
 * @property endedAtEpochMillis 權威移除交易的 UTC 毫秒時間。
 * @property completed 移除時整場是否已正常結束；不單獨證明 Replay 完整。
 */
internal data class HistoryTerminalRecord(
    val matchId: String,
    val tableId: String,
    val endedAtEpochMillis: Long,
    val completed: Boolean,
)

/**
 * 單一伺服器存檔的 SQLite 歷史資料庫邊界。
 *
 * 此 adapter 的操作會同步進行 JDBC I/O；呼叫端必須在專用 I/O dispatcher 執行。
 * 每筆操作自行取得並關閉連線，資料庫物件不持有長期連線或檔案鎖。
 *
 * @property path 資料庫檔案的固定位置。
 * @property database 執行交易的 Exposed 連線設定。
 */
internal class SqliteHistoryDatabase private constructor(
    val path: Path,
    private val database: Database,
) {
    /** 同鍵、同內容重送視為成功；同鍵、不同內容拒絕且不覆寫原事件。
     *
     * @param record 待寫入的歷史事件。
     */
    fun appendPending(record: PendingHistoryRecord) = appendPendingBatch(listOf(record))

    /** 同一批事件在單一 SQLite 交易內提交；任一衝突會使整批回滾。
     *
     * @param records 待寫入的歷史事件批次。
     */
    fun appendPendingBatch(records: List<PendingHistoryRecord>) {
        records.forEach { record ->
            require(record.matchId.isNotBlank()) { "History match ID must not be blank" }
            require(record.sequence > 0L) { "History event sequence must be positive" }
            require(record.payloadVersion > 0) { "History payload version must be positive" }
        }
        if (records.isEmpty()) return
        transaction(database) {
            records.forEach { record -> appendPendingInTransaction(record) }
        }
    }

    /** 在呼叫端的 SQLite 交易中插入或核對單筆事件。 */
    private fun appendPendingInTransaction(record: PendingHistoryRecord) {
        if (isTombstoned(record.matchId)) return
        HistoryPendingEventTable.insertIgnore {
            it[matchId] = record.matchId
            it[sequence] = record.sequence
            it[roundNumber] = record.roundNumber
            it[occurredAtEpochMillis] = record.occurredAtEpochMillis
            it[payloadVersion] = record.payloadVersion
            it[payload] = record.payload
        }
        val existing = HistoryPendingEventTable.selectAll().where {
            (HistoryPendingEventTable.matchId eq record.matchId) and
                (HistoryPendingEventTable.sequence eq record.sequence)
        }.single()
        check(
            existing[HistoryPendingEventTable.roundNumber] == record.roundNumber &&
                existing[HistoryPendingEventTable.occurredAtEpochMillis] == record.occurredAtEpochMillis &&
                existing[HistoryPendingEventTable.payloadVersion] == record.payloadVersion &&
                existing[HistoryPendingEventTable.payload] == record.payload,
        ) { "History event identity conflicts with existing content" }
    }

    /** 按權威序號讀取指定場次的原始暫存事件。
     *
     * @param matchId 對局穩定識別碼。
     * @return 依事件序號排序的暫存事件。
     */
    fun readPending(matchId: String): List<PendingHistoryRecord> = transaction(database) {
        HistoryPendingEventTable.selectAll().where { HistoryPendingEventTable.matchId eq matchId }
            .orderBy(HistoryPendingEventTable.sequence)
            .map { row ->
                PendingHistoryRecord(
                    matchId = row[HistoryPendingEventTable.matchId],
                    sequence = row[HistoryPendingEventTable.sequence],
                    roundNumber = row[HistoryPendingEventTable.roundNumber],
                    occurredAtEpochMillis = row[HistoryPendingEventTable.occurredAtEpochMillis],
                    payloadVersion = row[HistoryPendingEventTable.payloadVersion],
                    payload = row[HistoryPendingEventTable.payload],
                )
            }
    }

    /** 讀取所有未封存事件，供啟動時與權威 outbox 對帳。 */
    fun readAllPending(): List<PendingHistoryRecord> = transaction(database) {
        HistoryPendingEventTable.selectAll()
            .orderBy(HistoryPendingEventTable.matchId to SortOrder.ASC, HistoryPendingEventTable.sequence to SortOrder.ASC)
            .map { row ->
                PendingHistoryRecord(
                    row[HistoryPendingEventTable.matchId],
                    row[HistoryPendingEventTable.sequence],
                    row[HistoryPendingEventTable.roundNumber],
                    row[HistoryPendingEventTable.occurredAtEpochMillis],
                    row[HistoryPendingEventTable.payloadVersion],
                    row[HistoryPendingEventTable.payload],
                )
            }
    }

    /** 讀取已確認缺口，不以無內容事件填補。 */
    fun readGaps(): Map<String, Long> = transaction(database) {
        HistoryGapTable.selectAll().associate { it[HistoryGapTable.matchId] to it[HistoryGapTable.firstMissingSequence] }
    }

    /** 保留每場最早缺口；重複對帳不會將缺口推後。
     *
     * @param gaps 對局 ID 與最早缺失序號的對照。
     */
    fun recordGaps(gaps: Map<String, Long>) {
        if (gaps.isEmpty()) return
        transaction(database) {
            gaps.forEach { (matchId, sequence) ->
                if (isTombstoned(matchId)) return@forEach
                require(sequence > 0L) { "Missing history sequence must be positive" }
                HistoryGapTable.insertIgnore {
                    it[HistoryGapTable.matchId] = matchId
                    it[firstMissingSequence] = sequence
                }
                val existing = HistoryGapTable.selectAll().where { HistoryGapTable.matchId eq matchId }
                    .single()[HistoryGapTable.firstMissingSequence]
                HistoryGapTable.update({ HistoryGapTable.matchId eq matchId }) {
                    it[firstMissingSequence] = minOf(existing, sequence)
                }
            }
        }
    }

    /**
     * 保存設定停止診斷；相同原因重送視為成功，不覆寫不同原因。
     *
     * @param stops 對局 UUID 字串與穩定停止原因的對照。
     */
    fun recordRecordingStops(stops: Map<String, String>) {
        if (stops.isEmpty()) return
        transaction(database) {
            stops.forEach { (matchId, reason) ->
                if (isTombstoned(matchId)) return@forEach
                require(matchId.isNotBlank()) { "History match ID must not be blank" }
                require(reason.isNotBlank()) { "History recording stop reason must not be blank" }
                HistoryRecordingStopTable.insertIgnore {
                    it[HistoryRecordingStopTable.matchId] = matchId
                    it[HistoryRecordingStopTable.reason] = reason
                }
                val existing = HistoryRecordingStopTable.selectAll()
                    .where { HistoryRecordingStopTable.matchId eq matchId }
                    .single()[HistoryRecordingStopTable.reason]
                check(existing == reason) { "History recording stop reason conflicts with existing content" }
            }
        }
    }

    /**
     * 讀取設定停止診斷，供完整封存防護及記錄狀態確認。
     *
     * @return 對局 UUID 字串與穩定停止原因的對照。
     */
    fun readRecordingStops(): Map<String, String> = transaction(database) {
        HistoryRecordingStopTable.selectAll().associate {
            it[HistoryRecordingStopTable.matchId] to it[HistoryRecordingStopTable.reason]
        }
    }

    /** 已封存場次 ID；用於略過其原始事件的缺口推導。 */
    fun readReplayIds(): Set<String> = transaction(database) {
        HistoryReplayTable.selectAll().mapTo(mutableSetOf()) { it[HistoryReplayTable.matchId] }
    }

    /**
     * 以資料庫端大小檢查讀取單場 Replay；不把超大內容載入 Kotlin 記憶體。
     *
     * @param matchId 欲讀取的對局識別碼。
     * @param maximumBytes 允許載入與解析的 UTF-8 位元組上限。
     * @return 缺少、超過大小限制，或受限的 Replay JSON。
     */
    fun readReplayPayload(matchId: String, maximumBytes: Int): HistoryReplayPayloadRead = transaction(database) {
        require(matchId.isNotBlank()) { "History match ID must not be blank" }
        require(maximumBytes > 0) { "History replay size limit must be positive" }
        val payloadBytes = TransactionManager.current().exec(
            "SELECT length(CAST(payload AS BLOB)) FROM history_replay WHERE match_id = ?",
            listOf(VarCharColumnType() to matchId),
        ) { result ->
            if (result.next()) result.getLong(1) else null
        } ?: return@transaction HistoryReplayPayloadRead.Missing
        if (payloadBytes > maximumBytes.toLong()) {
            HistoryReplayPayloadRead.TooLarge
        } else {
            val payload = HistoryReplayTable.select(HistoryReplayTable.payload)
                .where { HistoryReplayTable.matchId eq matchId }
                .single()[HistoryReplayTable.payload]
            HistoryReplayPayloadRead.Found(payload)
        }
    }

    /**
     * 讀取單場保存狀態所需的最小 SQL 證據，不載入 Replay payload。
     *
     * @param matchId 欲查詢的對局識別碼。
     * @return 單一交易內取得的保存證據。
     */
    fun readArchiveStatusEvidence(matchId: String): HistoryArchiveStatusEvidence = transaction(database) {
        val replay = HistoryReplayTable.selectAll().where { HistoryReplayTable.matchId eq matchId }.any()
        val tombstone = HistoryTombstoneTable.selectAll().where { HistoryTombstoneTable.matchId eq matchId }.any()
        val pending = HistoryPendingEventTable.selectAll().where { HistoryPendingEventTable.matchId eq matchId }.any()
        val failed = HistoryGapTable.selectAll().where { HistoryGapTable.matchId eq matchId }.any() ||
            HistoryRecordingStopTable.selectAll().where { HistoryRecordingStopTable.matchId eq matchId }.any() ||
            HistoryTerminalTable.selectAll().where {
                (HistoryTerminalTable.matchId eq matchId) and (HistoryTerminalTable.completed eq false)
            }.any()
        val participants = HistoryParticipantTable.select(HistoryParticipantTable.playerId)
            .where { HistoryParticipantTable.matchId eq matchId }
            .mapTo(mutableSetOf()) { it[HistoryParticipantTable.playerId] }
        HistoryArchiveStatusEvidence(replay, tombstone, pending, failed, participants)
    }

    /**
     * 查詢至多一百個生成場次的封存及清理證據，不反序列化 Replay。
     *
     * @param matchIds 此批工作建立的場次 ID。
     * @return 仍存在的 Replay 大小、已清理 ID 及目前磁碟大小。
     */
    fun readGenerationReceipt(matchIds: Set<String>): HistoryGenerationReceipt {
        require(matchIds.size <= 100) { "History generation receipt is limited to 100 matches" }
        val sizes = mutableMapOf<String, Long>()
        val pruned = mutableSetOf<String>()
        if (matchIds.isNotEmpty()) {
            transaction(database) {
                val placeholders = matchIds.joinToString(",") { "?" }
                val arguments = matchIds.map { VarCharColumnType() to it }
                TransactionManager.current().exec("SELECT match_id, length(CAST(payload AS BLOB)) FROM history_replay WHERE match_id IN ($placeholders)", arguments) { rows ->
                    while (rows.next()) sizes[rows.getString(1)] = rows.getLong(2)
                }
                TransactionManager.current().exec("SELECT match_id FROM history_tombstone WHERE match_id IN ($placeholders)", arguments) { rows ->
                    while (rows.next()) pruned += rows.getString(1)
                }
            }
        }
        return HistoryGenerationReceipt(sizes, pruned, measureDiskUsage())
    }

    /** 完成資料表摘要與 Replay 的單一交易；成功後才刪除原始事件。 */
    fun archive(record: HistoryArchiveRecord): Boolean = transaction(database) {
        if (isTombstoned(record.matchId)) return@transaction false
        check(
            HistoryRecordingStopTable.selectAll().where { HistoryRecordingStopTable.matchId eq record.matchId }.empty(),
        ) { "History replay cannot be archived after recording stopped" }
        val existing = HistoryReplayTable.selectAll().where { HistoryReplayTable.matchId eq record.matchId }.singleOrNull()
        if (existing != null) {
            check(existing[HistoryReplayTable.payload] == record.replayPayload) { "History replay identity conflicts with existing content" }
            return@transaction false
        }
        check(HistoryGapTable.selectAll().where { HistoryGapTable.matchId eq record.matchId }.empty()) {
            "History replay cannot be archived with a known sequence gap"
        }
        HistoryReplayTable.insert {
            it[matchId] = record.matchId
            it[formatVersion] = 1
            it[createdAtEpochMillis] = record.endedAtEpochMillis
            it[payload] = record.replayPayload
        }
        HistoryMatchTable.insert {
            it[matchId] = record.matchId
            it[tableId] = record.tableId
            it[ruleId] = record.ruleId
            it[dimensionId] = record.dimensionId
            it[status] = "COMPLETED"
            it[startedAtEpochMillis] = record.startedAtEpochMillis
            it[endedAtEpochMillis] = record.endedAtEpochMillis
        }
        record.participants.forEach { participant ->
            HistoryParticipantTable.insert {
                it[matchId] = record.matchId
                it[seatIndex] = participant.seatIndex
                it[playerId] = participant.playerId
                it[aiStrategyId] = participant.aiStrategyId
            }
        }
        HistoryResultProjectionTable.insert {
            it[matchId] = record.matchId
            it[durationMillis] = (record.endedAtEpochMillis - record.startedAtEpochMillis).takeIf { duration -> duration >= 0L }
        }
        record.participantResults.forEach { result ->
            HistoryParticipantResultTable.insert {
                it[matchId] = record.matchId
                it[seatIndex] = result.seatIndex
                it[finalScore] = result.finalScore
                it[finalRank] = result.finalRank
            }
        }
        record.rounds.forEach { round ->
            HistoryRoundTable.insert {
                it[matchId] = record.matchId
                it[roundNumber] = round.roundNumber
                it[startedAtEpochMillis] = round.startedAtEpochMillis
                it[endedAtEpochMillis] = round.endedAtEpochMillis
            }
        }
        HistoryPendingEventTable.deleteWhere { HistoryPendingEventTable.matchId eq record.matchId }
        true
    }

    /** 寫入終局摘要；已清理場次的延遲訊息會被安全忽略。
     *
     * @param terminals 權威狀態移除交易產生的終端摘要。
     */
    fun recordTerminals(terminals: List<HistoryTerminalRecord>) {
        if (terminals.isEmpty()) return
        terminals.forEach {
            require(it.matchId.isNotBlank()) { "History match ID must not be blank" }
            require(it.tableId.isNotBlank()) { "History table ID must not be blank" }
        }
        transaction(database) {
            terminals.forEach { terminal ->
                if (isTombstoned(terminal.matchId)) return@forEach
                HistoryTerminalTable.insertIgnore {
                    it[matchId] = terminal.matchId
                    it[tableId] = terminal.tableId
                    it[endedAtEpochMillis] = terminal.endedAtEpochMillis
                    it[completed] = terminal.completed
                }
                val existing = HistoryTerminalTable.selectAll().where {
                    (HistoryTerminalTable.matchId eq terminal.matchId) and
                        (HistoryTerminalTable.tableId eq terminal.tableId)
                }.single()
                check(
                    existing[HistoryTerminalTable.endedAtEpochMillis] == terminal.endedAtEpochMillis &&
                        existing[HistoryTerminalTable.completed] == terminal.completed,
                ) { "History terminal identity conflicts with existing content" }
            }
        }
    }

    /** 讀取清理墓碑，供啟動時過濾延遲 outbox 訊息。
     *
     * @return 已清理對局的穩定 ID 集合。
     */
    fun readTombstones(): Set<String> = transaction(database) {
        HistoryTombstoneTable.selectAll().mapTo(mutableSetOf()) { it[HistoryTombstoneTable.matchId] }
    }

    /**
     * 讀取已有終止證據及部分紀錄診斷的 ID，不載入事件或 Replay payload。
     *
     * @return 已由 SQL 保存、可精確確認權威 metadata 的部分場次。
     */
    fun readTerminalPartialIds(): Set<String> = transaction(database) {
        val diagnosed = HistoryGapTable.select(HistoryGapTable.matchId).mapTo(mutableSetOf()) { it[HistoryGapTable.matchId] } +
            HistoryRecordingStopTable.select(HistoryRecordingStopTable.matchId).map { it[HistoryRecordingStopTable.matchId] }
        HistoryTerminalTable.select(HistoryTerminalTable.matchId, HistoryTerminalTable.completed)
            .filter { !it[HistoryTerminalTable.completed] || it[HistoryTerminalTable.matchId] in diagnosed }
            .mapTo(mutableSetOf()) { it[HistoryTerminalTable.matchId] }
    }

    /** 以資料表識別欄位讀取保存分類與列數，不載入任何事件或 Replay 內容。
     *
     * @return 單一交易內取得的資料庫統計。
     */
    fun readStatistics(): HistoryDatabaseStatistics = transaction(database) {
        val tombstones = HistoryTombstoneTable.select(HistoryTombstoneTable.matchId)
            .mapTo(mutableSetOf()) { it[HistoryTombstoneTable.matchId] }
        val ids = mutableSetOf<String>().apply {
            addAll(HistoryMatchTable.select(HistoryMatchTable.matchId).map { it[HistoryMatchTable.matchId] })
            addAll(HistoryReplayTable.select(HistoryReplayTable.matchId).map { it[HistoryReplayTable.matchId] })
            addAll(HistoryPendingEventTable.select(HistoryPendingEventTable.matchId).withDistinct().map { it[HistoryPendingEventTable.matchId] })
            addAll(HistoryGapTable.select(HistoryGapTable.matchId).map { it[HistoryGapTable.matchId] })
            addAll(HistoryRecordingStopTable.select(HistoryRecordingStopTable.matchId).map { it[HistoryRecordingStopTable.matchId] })
            addAll(HistoryTerminalTable.select(HistoryTerminalTable.matchId).map { it[HistoryTerminalTable.matchId] })
            addAll(HistoryParticipantTable.select(HistoryParticipantTable.matchId).withDistinct().map { it[HistoryParticipantTable.matchId] })
            addAll(HistoryRoundTable.select(HistoryRoundTable.matchId).withDistinct().map { it[HistoryRoundTable.matchId] })
        }
        val complete = HistoryMatchTable.select(HistoryMatchTable.matchId).where {
            (HistoryMatchTable.status eq "COMPLETED") and HistoryMatchTable.endedAtEpochMillis.isNotNull()
        }.mapTo(mutableSetOf()) { it[HistoryMatchTable.matchId] }
        val replay = HistoryReplayTable.select(HistoryReplayTable.matchId).mapTo(mutableSetOf()) {
            it[HistoryReplayTable.matchId]
        }
        val interrupted = HistoryTerminalTable.select(HistoryTerminalTable.matchId).where { HistoryTerminalTable.completed eq false }
            .mapTo(mutableSetOf()) { it[HistoryTerminalTable.matchId] }
        val terminalIds = HistoryTerminalTable.select(HistoryTerminalTable.matchId).mapTo(mutableSetOf()) { it[HistoryTerminalTable.matchId] }
        val diagnosed = (
            HistoryGapTable.select(HistoryGapTable.matchId).map { it[HistoryGapTable.matchId] } +
                HistoryRecordingStopTable.select(HistoryRecordingStopTable.matchId).map { it[HistoryRecordingStopTable.matchId] }
            ).toSet()
        val states = ids.filterNot { it in tombstones }.associateWith { matchId ->
            when {
                matchId in complete && matchId in replay -> HistoryStoredMatchState.COMPLETED
                matchId in interrupted || (matchId in terminalIds && matchId in diagnosed) -> HistoryStoredMatchState.PARTIAL
                else -> HistoryStoredMatchState.UNKNOWN
            }
        }
        HistoryDatabaseStatistics(
            matchStates = states,
            pendingEventCount = HistoryPendingEventTable.selectAll().count(),
            tombstoneCount = HistoryTombstoneTable.selectAll().count(),
        )
    }

    /** 以結束時間與對局 ID 執行有界 keyset 分頁，不載入未回傳的摘要列。
     *
     * @param limit 單頁筆數，會限制在 1 至 100。
     * @param cursor 上一頁最後一列的排序游標。
     * @param excludedMatchIds 應在 SQL 限制與分頁前排除的對局 ID。
     * @return 完整或已確認部分場次的摘要頁。
     */
    fun readSummaryPage(
        limit: Int = 20,
        cursor: HistorySummaryCursor? = null,
        excludedMatchIds: Set<String> = emptySet(),
    ): HistorySummaryPage = transaction(database) {
        val boundedLimit = limit.coerceIn(1, 100)
        val excluded = excludedMatchIds.joinToString(",") { "?" }
        val exclusionSql = if (excluded.isEmpty()) "" else "AND match_id NOT IN ($excluded)"
        val cursorSql = if (cursor == null) {
            ""
        } else {
            "AND (ended_at_epoch_millis > ? OR (ended_at_epoch_millis = ? AND match_id > ?))"
        }
        val sql = """
            WITH terminals AS (
                SELECT *, ROW_NUMBER() OVER (PARTITION BY match_id ORDER BY ended_at_epoch_millis DESC, table_id ASC) AS row_rank
                FROM history_terminal
            ), candidates AS (
                SELECT m.match_id, 'COMPLETED' AS state, m.started_at_epoch_millis,
                    m.ended_at_epoch_millis, m.rule_id, m.table_id, m.dimension_id
                FROM history_match m
                JOIN history_replay r ON r.match_id = m.match_id
                WHERE m.status = 'COMPLETED' AND m.ended_at_epoch_millis IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM history_tombstone z WHERE z.match_id = m.match_id)
                UNION ALL
                SELECT t.match_id, 'PARTIAL', m.started_at_epoch_millis,
                    COALESCE(m.ended_at_epoch_millis, t.ended_at_epoch_millis),
                    m.rule_id, COALESCE(m.table_id, t.table_id), m.dimension_id
                FROM terminals t
                LEFT JOIN history_match m ON m.match_id = t.match_id
                WHERE t.row_rank = 1
                    AND NOT EXISTS (SELECT 1 FROM history_replay r JOIN history_match c ON c.match_id = r.match_id
                        WHERE r.match_id = t.match_id AND c.status = 'COMPLETED' AND c.ended_at_epoch_millis IS NOT NULL)
                    AND (EXISTS (SELECT 1 FROM history_terminal i WHERE i.match_id = t.match_id AND i.completed = 0)
                        OR EXISTS (SELECT 1 FROM history_gap g WHERE g.match_id = t.match_id)
                        OR EXISTS (SELECT 1 FROM history_recording_stop s WHERE s.match_id = t.match_id))
                    AND NOT EXISTS (SELECT 1 FROM history_tombstone z WHERE z.match_id = t.match_id)
            )
            SELECT match_id, state, started_at_epoch_millis, ended_at_epoch_millis, rule_id, table_id, dimension_id
            FROM candidates
            WHERE 1 = 1 $exclusionSql $cursorSql
            ORDER BY ended_at_epoch_millis ASC, match_id ASC
            LIMIT ${boundedLimit + 1}
        """.trimIndent()
        val rows = mutableListOf<HistoryStoredMatchSummary>()
        val arguments = excludedMatchIds.map { VarCharColumnType() to it } + if (cursor == null) {
            emptyList()
        } else {
            listOf(
                LongColumnType() to cursor.endedAtEpochMillis,
                LongColumnType() to cursor.endedAtEpochMillis,
                VarCharColumnType() to cursor.matchId,
            )
        }
        exec(sql, arguments, explicitStatementType = StatementType.SELECT) { result ->
            while (result.next()) rows += result.toStoredMatchSummary()
        }
        val hasNext = rows.size > boundedLimit
        val entries = rows.take(boundedLimit)
        HistorySummaryPage(
            entries = entries,
            nextCursor = if (hasNext) {
                entries.lastOrNull()?.let {
                    HistorySummaryCursor(it.endedAtEpochMillis, it.matchId)
                }
            } else {
                null
            },
        )
    }

    /**
     * 依已驗證的 Flow 查詢要求讀取安全摘要，不解碼 Replay 內容。
     *
     * @param query 已完成權限驗證、範圍限制與游標驗證的查詢。
     * @return 有界 keyset 摘要頁。
     */
    fun readHistoryQueryPage(query: SqliteHistoryQuery): SqliteHistoryQueryPage = transaction(database) {
        require(query.pageSize in 1..50) { "History query page size is out of range" }
        val arguments = mutableListOf<Pair<ColumnType<*>, Any?>>()

        /**
         * 將識別碼作為參數綁定，不將外部字串插入 SQL。
         *
         * @param value 識別碼或穩定狀態字串。
         */
        fun bindString(value: String) {
            arguments += VarCharColumnType() to value
        }

        /**
         * 將時間與游標值作為參數綁定。
         *
         * @param value 時間或排序界限。
         */
        fun bindLong(value: Long) {
            arguments += LongColumnType() to value
        }

        /**
         * 將名次界限作為參數綁定。
         *
         * @param value 名次界限。
         */
        fun bindInt(value: Int) {
            arguments += IntegerColumnType() to value
        }
        val endedExpression = "COALESCE(m.ended_at_epoch_millis, (SELECT MAX(t.ended_at_epoch_millis) FROM history_terminal t WHERE t.match_id = m.match_id))"
        val durationExpression = "CASE WHEN $endedExpression IS NULL OR m.started_at_epoch_millis IS NULL OR $endedExpression < m.started_at_epoch_millis THEN NULL ELSE $endedExpression - m.started_at_epoch_millis END"
        val outcomeExpression = "CASE WHEN (m.status = 'COMPLETED' AND EXISTS (SELECT 1 FROM history_replay r WHERE r.match_id = m.match_id)) OR EXISTS (SELECT 1 FROM history_terminal t WHERE t.match_id = m.match_id AND t.completed = 1) THEN 'COMPLETED' WHEN EXISTS (SELECT 1 FROM history_terminal t WHERE t.match_id = m.match_id AND t.completed = 0) THEN 'INTERRUPTED' ELSE NULL END"
        val conditions = mutableListOf<String>()
        conditions += "NOT EXISTS (SELECT 1 FROM history_tombstone z WHERE z.match_id = m.match_id)"
        if (!query.includeAll) {
            conditions += "EXISTS (SELECT 1 FROM history_participant member WHERE member.match_id = m.match_id)"
        }
        conditions += "((m.status = 'COMPLETED' AND m.ended_at_epoch_millis IS NOT NULL AND EXISTS (SELECT 1 FROM history_replay r WHERE r.match_id = m.match_id)) OR EXISTS (SELECT 1 FROM history_terminal t WHERE t.match_id = m.match_id))"
        query.excludedMatchIds.takeIf { it.isNotEmpty() }?.let { ids ->
            conditions += "m.match_id NOT IN (${ids.joinToString(",") { "?" }})"
            ids.forEach(::bindString)
        }
        query.matchId?.let {
            conditions += "m.match_id = ?"
            bindString(it)
        }
        query.participantIds?.let { participantIds ->
            if (participantIds.isEmpty()) {
                conditions += "1 = 0"
            } else {
                conditions += "EXISTS (SELECT 1 FROM history_participant named WHERE named.match_id = m.match_id AND named.player_id IN (${participantIds.joinToString(",") { "?" }}))"
                participantIds.forEach(::bindString)
            }
        }
        if (!query.includeAll) {
            val playerId = query.playerId ?: error("Own history query requires a player ID")
            conditions += "EXISTS (SELECT 1 FROM history_participant owner WHERE owner.match_id = m.match_id AND owner.player_id = ?)"
            bindString(playerId)
        }
        query.ruleId?.let {
            conditions += "m.rule_id = ?"
            bindString(it)
        }
        query.outcome?.let {
            conditions += "($outcomeExpression) = ?"
            bindString(if (it == SqliteHistoryOutcomeFilter.NORMAL_COMPLETED) "COMPLETED" else "INTERRUPTED")
        }
        query.aiFilter?.let {
            conditions += if (it == SqliteHistoryAiFilter.CONTAINS_AI) {
                "EXISTS (SELECT 1 FROM history_participant ai WHERE ai.match_id = m.match_id AND ai.ai_strategy_id IS NOT NULL)"
            } else {
                // 部分索引不能證明已列出所有參與者，因此不能由未見 AI 推論整場沒有 AI。
                "EXISTS (SELECT 1 FROM history_replay complete WHERE complete.match_id = m.match_id) AND " +
                    "EXISTS (SELECT 1 FROM history_participant known WHERE known.match_id = m.match_id) AND " +
                    "NOT EXISTS (SELECT 1 FROM history_participant ai WHERE ai.match_id = m.match_id AND ai.ai_strategy_id IS NOT NULL)"
            }
        }
        query.integrityFilter?.let {
            conditions += if (it == SqliteHistoryIntegrityFilter.COMPLETE) {
                "EXISTS (SELECT 1 FROM history_replay r WHERE r.match_id = m.match_id)"
            } else {
                "NOT EXISTS (SELECT 1 FROM history_replay r WHERE r.match_id = m.match_id)"
            }
        }
        query.endedAtLowerInclusive?.let {
            conditions += "$endedExpression >= ?"
            bindLong(it)
        }
        query.endedAtUpperExclusive?.let {
            conditions += "$endedExpression < ?"
            bindLong(it)
        }
        val resultJoin = "LEFT JOIN history_participant_result opr ON opr.match_id = m.match_id AND opr.seat_index = owner.seat_index"
        val ownRank = "opr.final_rank"
        val ownScore = "opr.final_score"
        query.minimumRank?.let {
            conditions += "$ownRank >= ?"
            bindInt(it)
        }
        query.maximumRank?.let {
            conditions += "$ownRank <= ?"
            bindInt(it)
        }
        val sortExpression = when (query.sortField) {
            SqliteHistorySortField.ENDED_AT -> endedExpression
            SqliteHistorySortField.DURATION -> durationExpression
            SqliteHistorySortField.OWN_RANK -> ownRank
            SqliteHistorySortField.OWN_SCORE -> ownScore
        }
        query.cursor?.let { cursor ->
            val direction = if (query.sortDirection == SqliteHistorySortDirection.ASC) ">" else "<"
            require(cursor.nullBucket == (cursor.sortValue == null)) { "History cursor null bucket does not match sort value" }
            val valueCondition = if (cursor.sortValue == null) {
                "($sortExpression IS NULL)"
            } else {
                bindLong(cursor.sortValue)
                "($sortExpression $direction ? OR ($sortExpression = ? AND m.match_id > ?))"
            }
            if (cursor.sortValue != null) {
                // 同值分支使用相同排序值，再以固定升序的對局 ID 接續。
                bindLong(cursor.sortValue)
                bindString(cursor.matchId)
                conditions += "($sortExpression IS NULL OR $valueCondition)"
            } else {
                bindString(cursor.matchId)
                conditions += "($sortExpression IS NULL AND m.match_id > ?)"
            }
        }
        val nullOrder = "CASE WHEN $sortExpression IS NULL THEN 1 ELSE 0 END ASC"
        val valueOrder = if (query.sortDirection == SqliteHistorySortDirection.ASC) "$sortExpression ASC" else "$sortExpression DESC"
        val sql = """
            SELECT m.match_id, m.table_id, m.status, m.started_at_epoch_millis, m.ended_at_epoch_millis, m.rule_id,
                $endedExpression AS ended_value,
                $sortExpression AS sort_value,
                $durationExpression AS duration_value,
                $ownRank AS own_rank, $ownScore AS own_score,
                CASE WHEN EXISTS (SELECT 1 FROM history_replay r WHERE r.match_id = m.match_id)
                    THEN 'COMPLETE' ELSE 'INCOMPLETE' END AS integrity,
                $outcomeExpression AS outcome,
                owner.player_id AS owner_player_id, owner.seat_index AS owner_seat_index
            FROM (
                SELECT match_id, table_id, rule_id, dimension_id, status,
                    started_at_epoch_millis, ended_at_epoch_millis
                FROM history_match
                UNION ALL
                SELECT t.match_id, MIN(t.table_id) AS table_id, NULL AS rule_id, NULL AS dimension_id,
                    CASE WHEN MAX(t.completed) = 1 THEN 'COMPLETED' ELSE 'INTERRUPTED' END AS status,
                    NULL AS started_at_epoch_millis, MAX(t.ended_at_epoch_millis) AS ended_at_epoch_millis
                FROM history_terminal t
                WHERE NOT EXISTS (SELECT 1 FROM history_match existing WHERE existing.match_id = t.match_id)
                GROUP BY t.match_id
            ) m
            LEFT JOIN history_participant owner ON owner.match_id = m.match_id
                ${if (query.includeAll) "AND owner.seat_index = (SELECT MIN(seat_index) FROM history_participant first_owner WHERE first_owner.match_id = m.match_id)" else "AND owner.player_id = ?"}
            $resultJoin
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY $nullOrder, $valueOrder, m.match_id ASC
            LIMIT ${query.pageSize + 1}
        """.trimIndent()
        if (!query.includeAll) {
            val playerId = query.playerId ?: error("Own history query requires a player ID")
            arguments.add(0, VarCharColumnType() to playerId)
        }
        val rows = mutableListOf<SqliteHistoryQueryEntry>()
        exec(sql, arguments, explicitStatementType = StatementType.SELECT) { result ->
            while (result.next()) {
                val matchId = result.getString("match_id")
                val participants = queryParticipants(matchId)
                val rounds = HistoryRoundTable.selectAll().where { HistoryRoundTable.matchId eq matchId }
                    .orderBy(HistoryRoundTable.roundNumber)
                    .map {
                        HistoryQueryRound(
                            roundNumber = it[HistoryRoundTable.roundNumber],
                            startedAtEpochMillis = it[HistoryRoundTable.startedAtEpochMillis],
                            endedAtEpochMillis = it[HistoryRoundTable.endedAtEpochMillis],
                        )
                    }
                val ended = result.getLong("ended_value").let { if (result.wasNull()) null else it }
                val started = result.getLong("started_at_epoch_millis").let { if (result.wasNull()) null else it }
                rows += SqliteHistoryQueryEntry(
                    matchId = matchId,
                    tableId = result.getString("table_id"),
                    state = if (result.getString("integrity") == "COMPLETE") HistoryStoredMatchState.COMPLETED else HistoryStoredMatchState.PARTIAL,
                    outcome = SqliteHistoryMatchOutcome.valueOf(result.getString("outcome")),
                    startedAtEpochMillis = started,
                    endedAtEpochMillis = ended,
                    ruleId = result.getString("rule_id"),
                    durationMillis = result.getLong("duration_value").let { if (result.wasNull()) null else it },
                    participants = participants,
                    ownFinalScore = result.getInt("own_score").let { if (result.wasNull()) null else it },
                    ownFinalRank = result.getInt("own_rank").let { if (result.wasNull()) null else it },
                    rounds = rounds,
                )
            }
        }
        val hasNext = rows.size > query.pageSize
        val entries = rows.take(query.pageSize)
        val next = if (hasNext) {
            entries.lastOrNull()?.let { entry ->
                val sortValue = when (query.sortField) {
                    SqliteHistorySortField.ENDED_AT -> entry.endedAtEpochMillis
                    SqliteHistorySortField.DURATION -> entry.durationMillis
                    SqliteHistorySortField.OWN_RANK -> entry.ownFinalRank?.toLong()
                    SqliteHistorySortField.OWN_SCORE -> entry.ownFinalScore?.toLong()
                }
                SqliteHistoryCursor(sortValue, entry.matchId, sortValue == null)
            }
        } else {
            null
        }
        SqliteHistoryQueryPage(entries, next)
    }

    /**
     * 在目前交易中讀取單一對局的參與者摘要。
     *
     * @param matchId 已通過查詢範圍與公開條件的對局 ID。
     * @return 依開局座位排序的參與者及可證實結果。
     */
    private fun queryParticipants(matchId: String): List<HistoryQueryParticipant> = HistoryParticipantTable.selectAll().where { HistoryParticipantTable.matchId eq matchId }
        .orderBy(HistoryParticipantTable.seatIndex)
        .map {
            val result = HistoryParticipantResultTable.selectAll().where {
                (HistoryParticipantResultTable.matchId eq matchId) and
                    (HistoryParticipantResultTable.seatIndex eq it[HistoryParticipantTable.seatIndex])
            }.singleOrNull()
            HistoryQueryParticipant(
                seatIndex = it[HistoryParticipantTable.seatIndex],
                playerId = it[HistoryParticipantTable.playerId],
                aiStrategyId = it[HistoryParticipantTable.aiStrategyId],
                finalScore = result?.get(HistoryParticipantResultTable.finalScore),
                finalRank = result?.get(HistoryParticipantResultTable.finalRank),
            )
        }

    /** 以 SQLite UTF-8 BLOB 長度計算指定對局的邏輯 payload 大小。
     *
     * @param matchIds 要計算的對局 ID 集合。
     * @return 指定對局的 Replay 與暫存事件 payload UTF-8 位元組總數。
     */
    fun logicalPayloadBytes(matchIds: Collection<String>): Long = transaction(database) {
        logicalPayloadBytesInTransaction(matchIds)
    }

    /** 讀取可供權威 outbox 對帳的清理確認；墓碑 ID 格式錯誤時拒絕啟動對帳。
     *
     * @return 已提交的清理確認清單。
     */
    fun readPruningConfirmations(): List<HistoryPruningConfirmation> = transaction(database) {
        HistoryTombstoneTable.selectAll().map { row ->
            val matchId = row[HistoryTombstoneTable.matchId]
            HistoryPruningConfirmation(
                matchId = runCatching { Uuid.parse(matchId) }
                    .getOrElse { throw IllegalStateException("History tombstone has malformed match ID", it) },
                prunedAtEpochMillis = row[HistoryTombstoneTable.prunedAtEpochMillis],
                reason = row[HistoryTombstoneTable.reason],
            )
        }
    }

    /** 讀取僅有可證實摘要或終端診斷的保留候選。
     *
     * @return 依終局時間排序的保留候選。
     */
    fun readRetentionCandidates(): List<HistoryRetentionCandidate> = transaction(database) { retentionCandidatesInTransaction() }

    /** 在目前 SQLite 交易內建立候選，不開啟另一個快照。 */
    private fun retentionCandidatesInTransaction(): List<HistoryRetentionCandidate> {
        val complete = HistoryMatchTable.selectAll().mapNotNull { summary ->
            val matchId = summary[HistoryMatchTable.matchId]
            val ended = summary[HistoryMatchTable.endedAtEpochMillis] ?: return@mapNotNull null
            if (summary[HistoryMatchTable.status] != "COMPLETED" ||
                HistoryReplayTable.selectAll().where { HistoryReplayTable.matchId eq matchId }.empty()
            ) {
                return@mapNotNull null
            }
            val bytes = logicalPayloadBytesInTransaction(setOf(matchId))
            HistoryRetentionCandidate(matchId, summary[HistoryMatchTable.startedAtEpochMillis], ended, false, bytes)
        }
        val partial = HistoryTerminalTable.selectAll().mapNotNull { terminal ->
            val matchId = terminal[HistoryTerminalTable.matchId]
            if (HistoryReplayTable.selectAll().where { HistoryReplayTable.matchId eq matchId }.any()) return@mapNotNull null
            val interrupted = !terminal[HistoryTerminalTable.completed] ||
                HistoryGapTable.selectAll().where { HistoryGapTable.matchId eq matchId }.any() ||
                HistoryRecordingStopTable.selectAll().where { HistoryRecordingStopTable.matchId eq matchId }.any()
            if (!interrupted) return@mapNotNull null
            val summary = HistoryMatchTable.selectAll().where { HistoryMatchTable.matchId eq matchId }.singleOrNull()
            val ended = terminal[HistoryTerminalTable.endedAtEpochMillis]
            val started = summary?.get(HistoryMatchTable.startedAtEpochMillis)
                ?: HistoryPendingEventTable.select(HistoryPendingEventTable.occurredAtEpochMillis).where { HistoryPendingEventTable.matchId eq matchId }
                    .minOfOrNull { it[HistoryPendingEventTable.occurredAtEpochMillis] }
                ?: ended
            HistoryRetentionCandidate(
                matchId = matchId,
                startedAtEpochMillis = started,
                endedAtEpochMillis = summary?.get(HistoryMatchTable.endedAtEpochMillis) ?: ended,
                interrupted = interrupted,
                logicalBytes = logicalPayloadBytesInTransaction(setOf(matchId)),
            )
        }
        return (complete + partial).distinctBy { it.matchId }.sortedBy { it.endedAtEpochMillis }
    }

    /**
     * 在目前 SQLite 交易中計算 payload 的 UTF-8 位元組數，不載入文字內容。
     *
     * @param matchIds 要計算的場次，同一 ID 只計一次。
     * @return Replay 與 SQL 暫存事件的邏輯大小。
     */
    private fun logicalPayloadBytesInTransaction(matchIds: Collection<String>): Long {
        if (matchIds.isEmpty()) return 0L
        var total = 0L
        matchIds.distinct().chunked(PAYLOAD_QUERY_BATCH_SIZE).forEach { batch ->
            val ids = batch.joinToString(",") { "?" }
            val arguments = batch.map { VarCharColumnType() to it }
            TransactionManager.current().exec("SELECT COALESCE(SUM(length(CAST(payload AS BLOB))), 0) FROM history_replay WHERE match_id IN ($ids)", arguments) { result ->
                if (result.next()) total = Math.addExact(total, result.getLong(1))
            }
            TransactionManager.current().exec("SELECT COALESCE(SUM(length(CAST(payload AS BLOB))), 0) FROM history_pending_event WHERE match_id IN ($ids)", arguments) { result ->
                if (result.next()) total = Math.addExact(total, result.getLong(1))
            }
        }
        return total
    }

    /**
     * 將摘要查詢列轉為保存場次摘要模型。
     *
     * @return 保留未知 metadata 的摘要。
     */
    private fun ResultSet.toStoredMatchSummary(): HistoryStoredMatchSummary = HistoryStoredMatchSummary(
        matchId = getString("match_id"),
        state = HistoryStoredMatchState.valueOf(getString("state")),
        startedAtEpochMillis = getLong("started_at_epoch_millis").let { if (wasNull()) null else it },
        endedAtEpochMillis = getLong("ended_at_epoch_millis"),
        ruleId = getString("rule_id"),
        tableId = getString("table_id"),
        dimensionId = getString("dimension_id"),
    )

    /** 原子地重新確認可清理條件、刪除場次資料並寫入墓碑。
     *
     * @param reasons 對局 ID 與清理原因名稱的對照。
     * @param prunedAtEpochMillis 清理交易的 UTC 毫秒時間。
     * @return 實際寫入清理墓碑的場次數量。
     */
    fun pruneMatches(reasons: Map<String, String>, prunedAtEpochMillis: Long): Int = transaction(database) {
        require(prunedAtEpochMillis >= 0L) { "History prune timestamp must not be negative" }
        val eligible = retentionCandidatesInTransaction().mapTo(mutableSetOf()) { it.matchId }
        var count = 0
        reasons.forEach { (matchId, reason) ->
            require(matchId.isNotBlank()) { "History match ID must not be blank" }
            require(reason.isNotBlank()) { "History prune reason must not be blank" }
            if (HistoryTombstoneTable.selectAll().where { HistoryTombstoneTable.matchId eq matchId }.any()) return@forEach
            if (matchId !in eligible) return@forEach
            HistoryReplayTable.deleteWhere { HistoryReplayTable.matchId eq matchId }
            HistoryParticipantResultTable.deleteWhere { HistoryParticipantResultTable.matchId eq matchId }
            HistoryResultProjectionTable.deleteWhere { HistoryResultProjectionTable.matchId eq matchId }
            HistoryParticipantTable.deleteWhere { HistoryParticipantTable.matchId eq matchId }
            HistoryRoundTable.deleteWhere { HistoryRoundTable.matchId eq matchId }
            HistoryPendingEventTable.deleteWhere { HistoryPendingEventTable.matchId eq matchId }
            HistoryGapTable.deleteWhere { HistoryGapTable.matchId eq matchId }
            HistoryRecordingStopTable.deleteWhere { HistoryRecordingStopTable.matchId eq matchId }
            HistoryTerminalTable.deleteWhere { HistoryTerminalTable.matchId eq matchId }
            HistoryMatchTable.deleteWhere { HistoryMatchTable.matchId eq matchId }
            HistoryTombstoneTable.insert {
                it[HistoryTombstoneTable.matchId] = matchId
                it[HistoryTombstoneTable.prunedAtEpochMillis] = prunedAtEpochMillis
                it[HistoryTombstoneTable.reason] = reason
            }
            count++
        }
        count
    }

    /** 取得主資料庫與 SQLite sidecar 檔案的實際大小。
     *
     * @return 三個檔案的大小摘要。
     */
    fun measureDiskUsage(): HistoryDiskUsage = HistoryDiskUsage(
        dbBytes = fileSize(path),
        walBytes = sidecarSize(Path.of("$path-wal")),
        shmBytes = sidecarSize(Path.of("$path-shm")),
    )

    /** 先截斷 WAL，再以有界 incremental vacuum 回收頁面。
     *
     * @return 回收是否因資料庫忙碌而延後及是否支援 incremental vacuum。
     */
    fun recoverDiskSpace(): HistoryDiskRecovery {
        var busy = false
        var supported = false
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            try {
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA auto_vacuum").use { result ->
                        check(result.next()) { "History auto-vacuum mode is unavailable" }
                        supported = result.getInt(1) == INCREMENTAL_AUTO_VACUUM_MODE
                    }
                    statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)").use { result ->
                        check(result.next()) { "History checkpoint result is unavailable" }
                        busy = result.getInt(1) != 0
                    }
                    if (!busy && supported) {
                        statement.execute("PRAGMA incremental_vacuum($VACUUM_PAGE_BATCH)")
                        statement.resultSet?.use { result -> while (result.next()) { /* 消耗有界回收步驟。 */ } }
                    }
                }
            } catch (error: SQLException) {
                if (error.errorCode != SQLITE_BUSY && error.errorCode != SQLITE_LOCKED) throw error
                busy = true
            }
        }
        return HistoryDiskRecovery(busy = busy, incrementalSupported = supported)
    }

    /** 判斷場次是否已有清理墓碑；須在同一交易中呼叫。 */
    private fun isTombstoned(matchId: String): Boolean = HistoryTombstoneTable.selectAll()
        .where { HistoryTombstoneTable.matchId eq matchId }.any()

    /** 讀取檔案大小；主資料庫大小未知時不得回報零。 */
    private fun fileSize(file: Path): Long = runCatching { Files.size(file) }
        .getOrElse { throw IllegalStateException("History database size is unavailable", it) }

    /** 不存在的 SQLite sidecar 視為零；存在但大小未知時回報錯誤。 */
    private fun sidecarSize(file: Path): Long = if (Files.notExists(file)) 0L else fileSize(file)

    companion object {
        /** 單次大小查詢的參數數量上限，避免不限場數時超出 SQLite bind 限制。 */
        private const val PAYLOAD_QUERY_BATCH_SIZE = 500

        /** SQLite incremental auto-vacuum 模式。 */
        private const val INCREMENTAL_AUTO_VACUUM_MODE = 2

        /** 單次最多回收的頁面數，避免全量重建。 */
        private const val VACUUM_PAGE_BATCH = 256

        /** SQLite 有其他連線占用的錯誤碼。 */
        private const val SQLITE_BUSY = 5

        /** SQLite 表或資料庫被鎖定的錯誤碼。 */
        private const val SQLITE_LOCKED = 6

        /** 此 SQLite schema 的版本；與 Replay 文件及 Minecraft 權威存檔版本分離。 */
        const val SCHEMA_VERSION: Int = 1

        /**
         * 開啟既有資料庫或建立全新的 schema v1。
         *
         * 既有檔案若損壞、缺少必要結構或版本不符，保留原檔並回報失敗，不猜測或自動修復。
         *
         * @param path 歷史資料庫檔案位置。
         * @return 已驗證並準備交易的資料庫邊界。
         */
        fun open(path: Path): SqliteHistoryDatabase {
            val normalized = path.toAbsolutePath().normalize()
            val existed = Files.exists(normalized)
            if (!existed) Files.createDirectories(normalized.parent)
            val url = "jdbc:sqlite:$normalized"
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA integrity_check").use { result ->
                        check(result.next() && result.getString(1) == "ok") { "History database integrity check failed" }
                    }
                    if (existed) {
                        val present = mutableSetOf<String>()
                        statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { result ->
                            while (result.next()) present += result.getString(1)
                        }
                        val missingTables = historySchemaV1Tables.map { it.tableName }.filterNot { it in present }.sorted()
                        check(missingTables.isEmpty()) {
                            "History database is missing required schema tables: ${missingTables.joinToString(", ")}"
                        }
                        historySchemaV1Tables.forEach { table ->
                            val columns = mutableSetOf<String>()
                            var dimensionIsNullable = false
                            statement.executeQuery("PRAGMA table_info('${table.tableName}')").use { result ->
                                while (result.next()) {
                                    val name = result.getString("name")
                                    columns += name
                                    if (table == HistoryMatchTable && name == "dimension_id") {
                                        dimensionIsNullable = result.getInt("notnull") == 0
                                    }
                                }
                            }
                            check(columns.containsAll(table.columns.map { it.name })) {
                                "History database is missing required schema columns"
                            }
                            if (table == HistoryMatchTable) {
                                check(dimensionIsNullable) { "History database has an incompatible dimension column" }
                            }
                        }
                    } else {
                        statement.execute("PRAGMA auto_vacuum = INCREMENTAL")
                    }
                }
            }
            val database = Database.connect(url = url, driver = "org.sqlite.JDBC")
            transaction(database) {
                if (!existed) {
                    SchemaUtils.create(*historySchemaV1Tables)
                    HistorySchemaVersionTable.insert {
                        it[id] = 1
                        it[version] = SCHEMA_VERSION
                    }
                } else {
                    val versions = HistorySchemaVersionTable.selectAll().toList()
                    check(versions.size == 1 && versions.single()[HistorySchemaVersionTable.id] == 1) {
                        "History database has invalid schema metadata"
                    }
                    check(versions.single()[HistorySchemaVersionTable.version] == SCHEMA_VERSION) {
                        "History database schema version is unsupported"
                    }
                }
            }
            return SqliteHistoryDatabase(normalized, database)
        }
    }
}
