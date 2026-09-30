package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryPruningConfirmation
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
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
 * @property roundNumber 局數。
 * @property startedAtEpochMillis 開局時間。
 * @property endedAtEpochMillis 結束時間；未正常結束時為 null。
 */
internal data class HistoryRoundRecord(val roundNumber: Int, val startedAtEpochMillis: Long, val endedAtEpochMillis: Long?)

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
            val bytes = HistoryReplayTable.select(HistoryReplayTable.payload).where { HistoryReplayTable.matchId eq matchId }
                .single()[HistoryReplayTable.payload].toByteArray(Charsets.UTF_8).size.toLong()
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
                logicalBytes = HistoryPendingEventTable.select(HistoryPendingEventTable.payload).where { HistoryPendingEventTable.matchId eq matchId }
                    .sumOf { it[HistoryPendingEventTable.payload].toByteArray(Charsets.UTF_8).size.toLong() },
            )
        }
        return (complete + partial).distinctBy { it.matchId }.sortedBy { it.endedAtEpochMillis }
    }

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
         * 既有檔案若損壞、缺少版本或版本較新，保留原檔並回報失敗，不猜測或自動修復。
         */
        /**
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
