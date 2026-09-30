package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * 與記錄資格分離的歷史保留政策。
 *
 * @property includeInterruptedMatches 是否保留已確認不可接續的部分紀錄。
 * @property maxMatches 已結束紀錄的場數上限；0 為不限。
 * @property retentionDuration 保留期限；零為不按天數清理。
 * @property maxDiskBytes 主檔與附屬檔的磁碟管理上限。
 */
internal data class HistoryRetentionPolicy(
    val includeInterruptedMatches: Boolean,
    val maxMatches: Int,
    val retentionDuration: Duration,
    val maxDiskBytes: Long,
) {
    init {
        require(maxMatches >= 0) { "History match limit must not be negative" }
        require(retentionDuration >= Duration.ZERO) { "History retention duration must not be negative" }
        require(maxDiskBytes > 0) { "History disk limit must be positive" }
    }
}

/**
 * 已證實不可接續、可按政策清理的單場摘要。
 *
 * @property matchId 對局穩定 ID。
 * @property startedAtEpochMillis 開局 UTC 時間。
 * @property endedAtEpochMillis 結束或確認終止 UTC 時間。
 * @property interrupted 是否為部分或中止紀錄，而非完整 Replay。
 * @property logicalBytes 事件或 Replay 的 UTF-8 資料大小；不等於可回收的磁碟量。
 */
internal data class HistoryRetentionCandidate(
    val matchId: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val interrupted: Boolean,
    val logicalBytes: Long = 0,
)

/** 清理單場紀錄的穩定原因。 */
internal enum class HistoryCleanupReason {
    /** 設定不保留部分紀錄。 */
    INTERRUPTED_EXCLUDED,

    /** 已達保留期限。 */
    EXPIRED,

    /** 超過已結束場數上限。 */
    MATCH_LIMIT,

    /** 實際磁碟超過管理上限。 */
    DISK_LIMIT,
}

/**
 * 唯讀評估結果；執行時仍須重新確認保護名單。
 *
 * @property evaluatedAt 評估時刻。
 * @property policy 評估使用的不可變有效政策。
 * @property removals 欲清理的對局 ID 與主要原因，保持確定性順序。
 * @property remaining 排除場數與期限候選後的最舊優先名單，供磁碟階段逐批評估。
 */
internal data class HistoryCleanupPlan(
    val evaluatedAt: Instant,
    val policy: HistoryRetentionPolicy,
    val removals: Map<String, HistoryCleanupReason>,
    val remaining: List<HistoryRetentionCandidate>,
)

/**
 * 從有效設定建立不包含平台執行物件的保留政策。
 *
 * @return 設定值的不可變政策快照。
 */
internal fun MinecraftHistoryConfig.retentionPolicy(): HistoryRetentionPolicy = HistoryRetentionPolicy(
    includeInterruptedMatches,
    maxMatches,
    retentionDuration,
    maxDiskBytes,
)

/**
 * 以相同純函式評估 preview 與實際清理；不存取資料庫或修改權威狀態。
 *
 * @param candidates 已確認終止或完整封存的候選。
 * @param protectedMatchIds 可接續或待確認場次，不得加入刪除名單。
 * @param policy 此次有效保留政策。
 * @param now 注入的 UTC 時刻。
 * @return 分階段且最舊優先的清理計畫。
 */
internal fun evaluateHistoryCleanup(
    candidates: List<HistoryRetentionCandidate>,
    protectedMatchIds: Set<String>,
    policy: HistoryRetentionPolicy,
    now: Instant,
): HistoryCleanupPlan {
    require(candidates.distinctBy { it.matchId }.size == candidates.size) { "History retention candidates must be unique" }
    val eligible = candidates.filterNot { it.matchId in protectedMatchIds }.sortedWith(
        compareBy(HistoryRetentionCandidate::endedAtEpochMillis, HistoryRetentionCandidate::startedAtEpochMillis, HistoryRetentionCandidate::matchId),
    )
    val removals = linkedMapOf<String, HistoryCleanupReason>()
    val retained = eligible.filter { candidate ->
        val reason = when {
            candidate.interrupted && !policy.includeInterruptedMatches -> HistoryCleanupReason.INTERRUPTED_EXCLUDED
            policy.retentionDuration > Duration.ZERO &&
                now - Instant.fromEpochMilliseconds(candidate.endedAtEpochMillis) >= policy.retentionDuration -> HistoryCleanupReason.EXPIRED
            else -> null
        }
        if (reason != null) removals[candidate.matchId] = reason
        reason == null
    }
    val overflow = if (policy.maxMatches == 0) 0 else (retained.size - policy.maxMatches).coerceAtLeast(0)
    retained.take(overflow).forEach { removals[it.matchId] = HistoryCleanupReason.MATCH_LIMIT }
    return HistoryCleanupPlan(now, policy, removals, retained.drop(overflow))
}
