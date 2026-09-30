package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import org.jetbrains.exposed.v1.core.Table

/** 歷史資料庫的獨立 schema 版本；不沿用權威世界存檔的版本。 */
internal object HistorySchemaVersionTable : Table("history_schema_version") {
    val id = integer("id")
    val version = integer("version")
    override val primaryKey = PrimaryKey(id)
}

/** 對局識別與伺服器側摘要欄位。 */
internal object HistoryMatchTable : Table("history_match") {
    val matchId = varchar("match_id", 36)
    val tableId = varchar("table_id", 36)
    val ruleId = varchar("rule_id", 255)
    val dimensionId = varchar("dimension_id", 255).nullable()
    val status = varchar("status", 32)
    val startedAtEpochMillis = long("started_at_epoch_millis")
    val endedAtEpochMillis = long("ended_at_epoch_millis").nullable()
    override val primaryKey = PrimaryKey(matchId)

    init {
        index(false, startedAtEpochMillis)
        index(false, status, endedAtEpochMillis)
    }
}

/** 對局開始時的座位與玩家識別。 */
internal object HistoryParticipantTable : Table("history_participant") {
    val matchId = varchar("match_id", 36)
    val seatIndex = integer("seat_index")
    val playerId = varchar("player_id", 36)
    val aiStrategyId = varchar("ai_strategy_id", 255).nullable()
    override val primaryKey = PrimaryKey(matchId, seatIndex)
}

/** 各局的穩定識別與起訖時間。 */
internal object HistoryRoundTable : Table("history_round") {
    val matchId = varchar("match_id", 36)
    val roundNumber = integer("round_number")
    val startedAtEpochMillis = long("started_at_epoch_millis")
    val endedAtEpochMillis = long("ended_at_epoch_millis").nullable()
    override val primaryKey = PrimaryKey(matchId, roundNumber)
}

/** 封存前的原始權威事件；同一場內依序號唯一。 */
internal object HistoryPendingEventTable : Table("history_pending_event") {
    val matchId = varchar("match_id", 36)
    val sequence = long("sequence")
    val roundNumber = integer("round_number")
    val occurredAtEpochMillis = long("occurred_at_epoch_millis")
    val payloadVersion = integer("payload_version")
    val payload = text("payload")
    override val primaryKey = PrimaryKey(matchId, sequence)

    init {
        index(false, matchId, roundNumber)
    }
}

/** 終局後由完整事件封存的精簡 Replay 文件。 */
internal object HistoryReplayTable : Table("history_replay") {
    val matchId = varchar("match_id", 36)
    val formatVersion = integer("format_version")
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val payload = text("payload")
    override val primaryKey = PrimaryKey(matchId)
}

/** 無法從權威 outbox 補齊的最早事件序號。 */
internal object HistoryGapTable : Table("history_gap") {
    val matchId = varchar("match_id", 36)
    val firstMissingSequence = long("first_missing_sequence")
    override val primaryKey = PrimaryKey(matchId)
}

/** 僅在明確的 schema v1 初始化交易中建立的資料表。 */
internal val historySchemaV1Tables: Array<Table> = arrayOf(
    HistorySchemaVersionTable,
    HistoryMatchTable,
    HistoryParticipantTable,
    HistoryRoundTable,
    HistoryPendingEventTable,
    HistoryReplayTable,
    HistoryGapTable,
)
