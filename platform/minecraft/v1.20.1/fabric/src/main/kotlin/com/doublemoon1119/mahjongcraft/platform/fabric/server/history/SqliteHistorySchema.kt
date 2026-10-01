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

    init {
        index(false, playerId, matchId)
    }
}

/** 已封存對局的可查詢結果投影；單場缺少投影列時，相關欄位保持未知。 */
internal object HistoryResultProjectionTable : Table("history_result_projection") {
    /** 對局的穩定 UUID 字串。 */
    val matchId = varchar("match_id", 36)

    /** 對局總持續時間；無法證實時為 null。 */
    val durationMillis = long("duration_millis").nullable()

    /** 每場對局只保存一份時間投影。 */
    override val primaryKey = PrimaryKey(matchId)

    init {
        index(false, durationMillis, matchId)
    }
}

/** 封存對局中每位參與者的最終結果投影。 */
internal object HistoryParticipantResultTable : Table("history_participant_result") {
    /** 對局的穩定 UUID 字串。 */
    val matchId = varchar("match_id", 36)

    /** 參與者的開局座位。 */
    val seatIndex = integer("seat_index")

    /** 最終分數；規則未提供時為 null。 */
    val finalScore = integer("final_score").nullable()

    /** 最終名次；規則未提供或無法排序時為 null。 */
    val finalRank = integer("final_rank").nullable()

    /** 每場對局的各開局座位只保存一份結果。 */
    override val primaryKey = PrimaryKey(matchId, seatIndex)

    init {
        index(false, matchId, finalRank)
        index(false, matchId, finalScore)
    }
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

/** 因記錄設定停用而停止追加歷史的對局診斷。 */
internal object HistoryRecordingStopTable : Table("history_recording_stop") {
    /** 對局的穩定 UUID 字串。 */
    val matchId = varchar("match_id", 36)

    /** 停止記錄的穩定原因名稱。 */
    val reason = varchar("reason", 255)

    /** 每場只保存一份停止診斷。 */
    override val primaryKey = PrimaryKey(matchId)
}

/** 已完成保留清理的場次墓碑，阻止延遲寫入重新建立資料。 */
internal object HistoryTombstoneTable : Table("history_tombstone") {
    /** 對局的穩定 UUID 字串。 */
    val matchId = varchar("match_id", 36)

    /** 清理完成的 UTC 毫秒時間戳。 */
    val prunedAtEpochMillis = long("pruned_at_epoch_millis")

    /** 清理原因。 */
    val reason = varchar("reason", 255)

    override val primaryKey = PrimaryKey(matchId)
}

/** 記錄器終局訊息；未完成終局可作為部分歷史保留候選。 */
internal object HistoryTerminalTable : Table("history_terminal") {
    /** 對局的穩定 UUID 字串。 */
    val matchId = varchar("match_id", 36)

    /** 終局所在牌桌的穩定 UUID 字串。 */
    val tableId = varchar("table_id", 36)

    /** 終局的 UTC 毫秒時間戳。 */
    val endedAtEpochMillis = long("ended_at_epoch_millis")

    /** 是否由完整終局流程確認。 */
    val completed = bool("completed")

    override val primaryKey = PrimaryKey(matchId, tableId)
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
    HistoryRecordingStopTable,
    HistoryTombstoneTable,
    HistoryTerminalTable,
    HistoryResultProjectionTable,
    HistoryParticipantResultTable,
)
