package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

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
 * 單一伺服器存檔的 SQLite 歷史資料庫邊界。
 *
 * 此 adapter 的操作會同步進行 JDBC I/O；呼叫端必須在專用 I/O dispatcher 執行。
 * 每筆操作自行取得並關閉連線，資料庫物件不持有長期連線或檔案鎖。
 *
 * @property path 資料庫檔案的固定位置。
 */
internal class SqliteHistoryDatabase private constructor(
    val path: Path,
    private val database: Database,
) {
    /** 同鍵、同內容重送視為成功；同鍵、不同內容拒絕且不覆寫原事件。 */
    fun appendPending(record: PendingHistoryRecord) = appendPendingBatch(listOf(record))

    /** 同一批事件在單一 SQLite 交易內提交；任一衝突會使整批回滾。 */
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

    /** 按權威序號讀取指定場次的原始暫存事件。 */
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

    /** 保留每場最早缺口；重複對帳不會將缺口推後。 */
    fun recordGaps(gaps: Map<String, Long>) {
        if (gaps.isEmpty()) return
        transaction(database) {
            gaps.forEach { (matchId, sequence) ->
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

    /** 已封存場次 ID；用於略過其原始事件的缺口推導。 */
    fun readReplayIds(): Set<String> = transaction(database) {
        HistoryReplayTable.selectAll().mapTo(mutableSetOf()) { it[HistoryReplayTable.matchId] }
    }

    /** 完成資料表摘要與 Replay 的單一交易；成功後才刪除原始事件。 */
    fun archive(record: HistoryArchiveRecord): Boolean = transaction(database) {
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

    companion object {
        /** 此 SQLite schema 的版本；與 Replay 文件及 Minecraft 權威存檔版本分離。 */
        const val SCHEMA_VERSION: Int = 1

        /**
         * 開啟既有資料庫或建立全新的 schema v1。
         *
         * 既有檔案若損壞、缺少版本或版本較新，保留原檔並回報失敗，不猜測或自動修復。
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
                        check(present.containsAll(historySchemaV1Tables.map { it.tableName })) {
                            "History database is missing required schema tables"
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
