package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

/** SQLite 查詢可用的排序欄位。 */
internal enum class SqliteHistorySortField {
    /** 依終局時間排序。 */
    ENDED_AT,

    /** 依對局持續時間排序。 */
    DURATION,

    /** 依查詢玩家的最終名次排序。 */
    OWN_RANK,

    /** 依查詢玩家的最終分數排序。 */
    OWN_SCORE,
}

/** SQLite 查詢的排序方向。 */
internal enum class SqliteHistorySortDirection {
    /** 升冪排序。 */
    ASC,

    /** 降冪排序。 */
    DESC,
}

/** 對局是否正常完成或由終端證據確認中斷。 */
internal enum class SqliteHistoryOutcomeFilter {
    /** 只包含正常完成的對局。 */
    NORMAL_COMPLETED,

    /** 只包含已確認中斷的對局。 */
    INTERRUPTED,
}

/** 封存摘要實際記錄的結束狀態。 */
internal enum class SqliteHistoryMatchOutcome {
    /** 權威流程確認正常完成。 */
    COMPLETED,

    /** 有可靠終端證據但未完成完整 Replay。 */
    INTERRUPTED,
}

/** 是否包含 AI 參與者的篩選。 */
internal enum class SqliteHistoryAiFilter {
    /** 只包含有 AI 的對局。 */
    CONTAINS_AI,

    /** 只包含沒有 AI 的對局。 */
    NO_AI,
}

/** 對局 Replay 完整性篩選。 */
internal enum class SqliteHistoryIntegrityFilter {
    /** 只包含完整 Replay。 */
    COMPLETE,

    /** 只包含有可靠終止證據但缺少完整 Replay 的摘要。 */
    INCOMPLETE,
}

/**
 * 已驗證且已綁定權限範圍的 SQLite 查詢。
 *
 * @property playerId 查詢玩家 UUID；全部範圍查詢時為 null。
 * @property includeAll 是否查詢所有已發布對局。
 * @property sortField 排序欄位。
 * @property sortDirection 排序方向。
 * @property ruleId 規則 ID 篩選；null 表示不篩選。
 * @property outcome 終局狀態篩選；null 表示不篩選。
 * @property aiFilter AI 參與者篩選；null 表示不篩選。
 * @property integrityFilter Replay 完整性篩選；null 表示不篩選。
 * @property endedAtLowerInclusive 終局時間下界，包含此值。
 * @property endedAtUpperExclusive 終局時間上界，不包含此值。
 * @property minimumRank 名次下界，包含此值。
 * @property maximumRank 名次上界，包含此值。
 * @property pageSize 單頁筆數。
 * @property cursor 上一頁最後一列的 keyset 游標。
 * @property excludedMatchIds 不可公開的活動或轉移中對局。
 * @property matchId 僅查詢指定對局時使用的 UUID；清單查詢為 null。
 */
internal data class SqliteHistoryQuery(
    val playerId: String?,
    val includeAll: Boolean,
    val sortField: SqliteHistorySortField,
    val sortDirection: SqliteHistorySortDirection,
    val ruleId: String? = null,
    val outcome: SqliteHistoryOutcomeFilter? = null,
    val aiFilter: SqliteHistoryAiFilter? = null,
    val integrityFilter: SqliteHistoryIntegrityFilter? = null,
    val endedAtLowerInclusive: Long? = null,
    val endedAtUpperExclusive: Long? = null,
    val minimumRank: Int? = null,
    val maximumRank: Int? = null,
    val pageSize: Int = 20,
    val cursor: SqliteHistoryCursor? = null,
    val excludedMatchIds: Set<String> = emptySet(),
    val matchId: String? = null,
)

/**
 * 不依賴資料列位置的 keyset 游標。
 *
 * @property sortValue 上一列的排序值；null 排序值以 [nullBucket] 表示。
 * @property matchId 上一列的對局 ID，用於同值穩定排序。
 * @property nullBucket 上一列是否屬於 null 排序群組。
 */
internal data class SqliteHistoryCursor(
    val sortValue: Long?,
    val matchId: String,
    val nullBucket: Boolean,
)

/**
 * SQLite 查詢回傳的安全摘要。
 *
 * @property matchId 對局 UUID。
 * @property state 完整或已確認中斷狀態。
 * @property startedAtEpochMillis 開始時間；無法證實時為 null。
 * @property endedAtEpochMillis 終局或中止確認時間。
 * @property ruleId 規則 ID；未知時為 null。
 * @property durationMillis 對局持續時間；未知時為 null。
 * @property participants 公開的參與者摘要。
 * @property ownFinalScore 查詢玩家的最終分數；未知時為 null。
 * @property ownFinalRank 查詢玩家的最終名次；未知時為 null。
 * @property outcome 權威終端狀態；與 Replay 完整性分開保存。
 * @property rounds 已知的局級摘要。
 */
internal data class SqliteHistoryQueryEntry(
    val matchId: String,
    val state: HistoryStoredMatchState,
    val outcome: SqliteHistoryMatchOutcome,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val ruleId: String?,
    val durationMillis: Long?,
    val participants: List<HistoryQueryParticipant>,
    val ownFinalScore: Int?,
    val ownFinalRank: Int?,
    val rounds: List<HistoryQueryRound>,
)

/**
 * 對局摘要中可公開的參與者資訊。
 *
 * @property seatIndex 開局座位。
 * @property playerId 玩家 UUID 字串。
 * @property aiStrategyId AI 策略 ID；真人玩家為 null。
 * @property finalScore 最終分數；資料不足時為 null。
 * @property finalRank 依規則比較器計算的最終名次；資料不足時為 null。
 */
internal data class HistoryQueryParticipant(
    val seatIndex: Int,
    val playerId: String,
    val aiStrategyId: String?,
    val finalScore: Int?,
    val finalRank: Int?,
)

/**
 * 對局中可公開的局級時間摘要。
 *
 * @property roundNumber 實際局序號。
 * @property startedAtEpochMillis 該局開始時間。
 * @property endedAtEpochMillis 該局結束時間。
 */
internal data class HistoryQueryRound(
    val roundNumber: Int,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
)

/**
 * 有界的 SQLite 對局摘要頁。
 *
 * @property entries 本頁安全摘要。
 * @property nextCursor 尚有下一頁時的 keyset 游標。
 */
internal data class SqliteHistoryQueryPage(
    val entries: List<SqliteHistoryQueryEntry>,
    val nextCursor: SqliteHistoryCursor?,
)
