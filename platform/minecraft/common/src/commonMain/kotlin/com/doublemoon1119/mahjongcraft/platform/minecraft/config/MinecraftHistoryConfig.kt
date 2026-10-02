package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/** 麻將歷史資料記錄與封存設定。
 *
 * @property enabled 是否啟用歷史資料記錄；停用會永久停止目前記錄，重新啟用只會記錄之後的新牌局。
 * @property includeAiMatches 是否記錄含有任一 AI 座位的牌局。
 * @property includeInterruptedMatches 是否保留已確認無法接續的中止或部分紀錄；正常關服後可接續的對局不算中止。
 * @property queryEnabled 是否允許玩家與管理員查詢已保存的歷史資料；停用時所有查詢都會被拒絕，但不影響記錄政策。
 * @property allowAdminQuery 是否允許具備管理員權限的使用者查詢全部已保存歷史；停用時管理員仍可查詢自己的對局。
 * @property queryMinimumIntervalMilliseconds 同一玩家接受兩次歷史查詢之間的最短間隔；重新載入只影響新查詢，不會取消已接受的查詢，且不得設為零以停用防護。
 * @property queryMaxOutstanding 全伺服器已接受但尚未完成回覆的歷史查詢工作上限，並非資料庫並行數；達到上限時新查詢會被拒絕，降低上限不取消已接受工作，且不得設為零以停用防護。
 * @property queryRejectionReplyIntervalMilliseconds 同一玩家收到准入限速或工作額滿拒絕回覆之間的最短間隔；不限制已接受工作的查詢結果，重新載入只影響後續拒絕回覆，且不得設為零以停用防護。
 * @property maxMatches 最多保留的已結束對局數；新對局先保存，再清除最舊的已結束對局，0 表示不限制場數，進行中對局不計入。
 * @property retentionDays 已結束對局自結束 UTC 時間起的保留天數；獨立於場數限制，到期即清理，0 表示不按天數清理。
 * @property maxDiskMiB 資料庫主檔及 WAL／SHM 附屬檔的磁碟上限，必須為正 MiB；容量不足時停止新增並標記缺口，不阻擋對局。
 */
data class MinecraftHistoryConfig(
    val enabled: Boolean = DEFAULT_ENABLED,
    val includeAiMatches: Boolean = DEFAULT_INCLUDE_AI_MATCHES,
    val includeInterruptedMatches: Boolean = DEFAULT_INCLUDE_INTERRUPTED_MATCHES,
    val queryEnabled: Boolean = DEFAULT_QUERY_ENABLED,
    val allowAdminQuery: Boolean = DEFAULT_ALLOW_ADMIN_QUERY,
    val queryMinimumIntervalMilliseconds: Long = DEFAULT_QUERY_MINIMUM_INTERVAL_MILLISECONDS,
    val queryMaxOutstanding: Int = DEFAULT_QUERY_MAX_OUTSTANDING,
    val queryRejectionReplyIntervalMilliseconds: Long = DEFAULT_QUERY_REJECTION_REPLY_INTERVAL_MILLISECONDS,
    val maxMatches: Int = DEFAULT_MAX_MATCHES,
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val maxDiskMiB: Long = DEFAULT_MAX_DISK_MIB,
) {
    init {
        require(maxMatches in MIN_MAX_MATCHES..MAX_MAX_MATCHES) {
            "history.max-matches must be between $MIN_MAX_MATCHES and $MAX_MAX_MATCHES, but was $maxMatches"
        }
        require(queryMinimumIntervalMilliseconds in MIN_QUERY_MINIMUM_INTERVAL_MILLISECONDS..MAX_QUERY_MINIMUM_INTERVAL_MILLISECONDS) {
            "history.query-minimum-interval-ms must be between $MIN_QUERY_MINIMUM_INTERVAL_MILLISECONDS and " +
                "$MAX_QUERY_MINIMUM_INTERVAL_MILLISECONDS, but was $queryMinimumIntervalMilliseconds"
        }
        require(queryMaxOutstanding in MIN_QUERY_MAX_OUTSTANDING..MAX_QUERY_MAX_OUTSTANDING) {
            "history.query-max-outstanding must be between $MIN_QUERY_MAX_OUTSTANDING and " +
                "$MAX_QUERY_MAX_OUTSTANDING, but was $queryMaxOutstanding"
        }
        require(
            queryRejectionReplyIntervalMilliseconds in
                MIN_QUERY_REJECTION_REPLY_INTERVAL_MILLISECONDS..MAX_QUERY_REJECTION_REPLY_INTERVAL_MILLISECONDS,
        ) {
            "history.query-rejection-reply-interval-ms must be between " +
                "$MIN_QUERY_REJECTION_REPLY_INTERVAL_MILLISECONDS and " +
                "$MAX_QUERY_REJECTION_REPLY_INTERVAL_MILLISECONDS, but was $queryRejectionReplyIntervalMilliseconds"
        }
        require(retentionDays in MIN_RETENTION_DAYS..MAX_RETENTION_DAYS) {
            "history.retention-days must be between $MIN_RETENTION_DAYS and $MAX_RETENTION_DAYS, but was $retentionDays"
        }
        require(maxDiskMiB in MIN_MAX_DISK_MIB..MAX_MAX_DISK_MIB) {
            "history.max-disk-mib must be between $MIN_MAX_DISK_MIB and $MAX_MAX_DISK_MIB, but was $maxDiskMiB"
        }
        require(maxDiskMiB <= Long.MAX_VALUE / BYTES_PER_MIB) {
            "history.max-disk-mib overflows the disk byte limit: $maxDiskMiB"
        }
    }

    /** 以 Duration 表示的保留期限；0 表示不按天數清理，不代表立即到期。 */
    val retentionDuration: Duration
        get() = retentionDays.days

    /** 以 Duration 表示的單一玩家歷史查詢間隔。 */
    val queryMinimumInterval: Duration
        get() = queryMinimumIntervalMilliseconds.milliseconds

    /** 以 Duration 表示的同一玩家拒絕回覆間隔。 */
    val queryRejectionReplyInterval: Duration
        get() = queryRejectionReplyIntervalMilliseconds.milliseconds

    /** 以位元組表示的歷史資料磁碟空間上限。 */
    val maxDiskBytes: Long
        get() = maxDiskMiB * BYTES_PER_MIB

    /** 歷史資料設定的預設值與合法範圍。 */
    companion object {
        /** 一 MiB 包含的位元組數量。 */
        const val BYTES_PER_MIB: Long = 1_048_576

        /** 是否啟用歷史資料記錄的預設值。 */
        const val DEFAULT_ENABLED: Boolean = true

        /** 是否記錄 AI 牌局的預設值。 */
        const val DEFAULT_INCLUDE_AI_MATCHES: Boolean = true

        /** 是否記錄中斷牌局的預設值。 */
        const val DEFAULT_INCLUDE_INTERRUPTED_MATCHES: Boolean = false

        /** 是否允許歷史查詢的預設值。 */
        const val DEFAULT_QUERY_ENABLED: Boolean = true

        /** 是否允許管理員查詢全部歷史的預設值。 */
        const val DEFAULT_ALLOW_ADMIN_QUERY: Boolean = true

        /** 玩家歷史查詢最短間隔的預設毫秒數。 */
        const val DEFAULT_QUERY_MINIMUM_INTERVAL_MILLISECONDS: Long = 250

        /** 全伺服器歷史查詢工作上限的預設值。 */
        const val DEFAULT_QUERY_MAX_OUTSTANDING: Int = 32

        /** 玩家收到歷史查詢拒絕回覆的最短間隔預設毫秒數。 */
        const val DEFAULT_QUERY_REJECTION_REPLY_INTERVAL_MILLISECONDS: Long = 1_000

        /** 最多保留牌局數量的預設值。 */
        const val DEFAULT_MAX_MATCHES: Int = 100

        /** 歷史資料保留天數的預設值。 */
        const val DEFAULT_RETENTION_DAYS: Int = 90

        /** 歷史資料磁碟空間上限的預設值。 */
        const val DEFAULT_MAX_DISK_MIB: Long = 256

        /** 整數設定允許的最小牌局數量。 */
        const val MIN_MAX_MATCHES: Int = 0

        /** 整數設定允許的最大牌局數量。 */
        const val MAX_MAX_MATCHES: Int = Int.MAX_VALUE

        /** 歷史查詢最短間隔允許的最小毫秒數。 */
        const val MIN_QUERY_MINIMUM_INTERVAL_MILLISECONDS: Long = 50

        /** 歷史查詢最短間隔允許的最大毫秒數。 */
        const val MAX_QUERY_MINIMUM_INTERVAL_MILLISECONDS: Long = 10_000

        /** 全伺服器歷史查詢工作上限允許的最小值。 */
        const val MIN_QUERY_MAX_OUTSTANDING: Int = 1

        /** 全伺服器歷史查詢工作上限允許的最大值。 */
        const val MAX_QUERY_MAX_OUTSTANDING: Int = 64

        /** 歷史查詢拒絕回覆間隔允許的最小毫秒數。 */
        const val MIN_QUERY_REJECTION_REPLY_INTERVAL_MILLISECONDS: Long = 250

        /** 歷史查詢拒絕回覆間隔允許的最大毫秒數。 */
        const val MAX_QUERY_REJECTION_REPLY_INTERVAL_MILLISECONDS: Long = 10_000

        /** 整數設定允許的最小保留天數。 */
        const val MIN_RETENTION_DAYS: Int = 0

        /** 整數設定允許的最大保留天數。 */
        const val MAX_RETENTION_DAYS: Int = Int.MAX_VALUE

        /** 磁碟空間設定允許的最小 MiB 數量。 */
        const val MIN_MAX_DISK_MIB: Long = 1

        /** 磁碟空間設定允許的最大 MiB 數量。 */
        const val MAX_MAX_DISK_MIB: Long = Long.MAX_VALUE / BYTES_PER_MIB
    }
}
