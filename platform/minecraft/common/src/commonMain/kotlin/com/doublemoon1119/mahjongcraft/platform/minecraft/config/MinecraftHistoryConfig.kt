package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** 麻將歷史資料記錄與封存設定。
 *
 * @property enabled 是否啟用歷史資料記錄；停用會永久停止目前記錄，重新啟用只會記錄之後的新牌局。
 * @property includeAiMatches 是否記錄含有任一 AI 座位的牌局。
 * @property includeInterruptedMatches 是否保留已確認無法接續的中止或部分紀錄；正常關服後可接續的對局不算中止。
 * @property maxMatches 最多保留的已結束對局數；新對局先保存，再清除最舊的已結束對局，0 表示不限制場數，進行中對局不計入。
 * @property retentionDays 已結束對局自結束 UTC 時間起的保留天數；獨立於場數限制，到期即清理，0 表示不按天數清理。
 * @property maxDiskMiB 資料庫主檔及 WAL／SHM 附屬檔的磁碟上限，必須為正 MiB；容量不足時停止新增並標記缺口，不阻擋對局。
 */
data class MinecraftHistoryConfig(
    val enabled: Boolean = DEFAULT_ENABLED,
    val includeAiMatches: Boolean = DEFAULT_INCLUDE_AI_MATCHES,
    val includeInterruptedMatches: Boolean = DEFAULT_INCLUDE_INTERRUPTED_MATCHES,
    val maxMatches: Int = DEFAULT_MAX_MATCHES,
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val maxDiskMiB: Long = DEFAULT_MAX_DISK_MIB,
) {
    init {
        require(maxMatches in MIN_MAX_MATCHES..MAX_MAX_MATCHES) {
            "history.max-matches must be between $MIN_MAX_MATCHES and $MAX_MAX_MATCHES, but was $maxMatches"
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
