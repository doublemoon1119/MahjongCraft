package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.time.Instant

/** 已保存場次的 SQL 分類，不表示權威 Game 是否仍存在。 */
internal enum class HistoryStoredMatchState {
    /** 摘要與完整 Replay 同時存在。 */
    COMPLETED,

    /** 已有終止證據的部分紀錄。 */
    PARTIAL,

    /** 尚無足夠完整或終止證據。 */
    UNKNOWN,
}

/**
 * 單一 SQL 讀取交易取得的統計，不包含事件或 Replay 內容。
 *
 * @property matchStates 每個已保存 matchId 的互斥分類。
 * @property pendingEventCount SQLite 尚未封存的事件列數。
 * @property tombstoneCount 已清理場次的收據列數。
 */
internal data class HistoryDatabaseStatistics(
    val matchStates: Map<String, HistoryStoredMatchState>,
    val pendingEventCount: Long,
    val tombstoneCount: Long,
)

/**
 * 目前存檔全部維度的歷史用量快照，未知或失敗不以零值取代。
 *
 * @property completedMatchCount 不在活動集合的完整場數。
 * @property activeMatchCount 具有記錄證據且仍存在權威 Game 的場數。
 * @property partialMatchCount 已確認終止、但不完整的非活動場數。
 * @property unknownMatchCount 無法證實完整或終止的非活動場數。
 * @property pendingSqlEventCount SQLite 尚待封存的事件數。
 * @property pendingOutboxEventCount 權威待寫事件數；不得與 SQL 事件數相加。
 * @property tombstoneCount 已清理場次的收據數，不算保存對局。
 * @property disk 主檔與 SQLite 附屬檔的實際大小。
 * @property policy 取得快照時固定的有效保留政策。
 * @property updatedAt 快照完成的 UTC 時刻。
 */
internal data class HistoryStorageSnapshot(
    val completedMatchCount: Long,
    val activeMatchCount: Long,
    val partialMatchCount: Long,
    val unknownMatchCount: Long,
    val pendingSqlEventCount: Long,
    val pendingOutboxEventCount: Long,
    val tombstoneCount: Long,
    val disk: HistoryDiskUsage,
    val policy: HistoryRetentionPolicy,
    val updatedAt: Instant,
)
