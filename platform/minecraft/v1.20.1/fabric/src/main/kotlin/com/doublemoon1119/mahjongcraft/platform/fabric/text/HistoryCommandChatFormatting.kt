package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryCleanupPreview
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryCleanupReason
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryCleanupReport
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryDiskUsage
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryRetentionPolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryStorageSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.Locale

/**
 * 建立與 config show 相同前綴、單行詳情標籤的歷史狀態訊息。
 *
 * @param status 不包含原始錯誤內容的安全狀態。
 * @return 可送出的本地化訊息。
 */
fun historyStatusMessage(status: HistoryWriterStatus): MutableText = prefixedConfigMessage(
    Text.translatable(
        MinecraftConfigCommandKeys.HISTORY_STATUS,
        bracketedInteractiveLabel(
            Text.translatable(MinecraftConfigCommandKeys.DETAILS),
            Text.translatable(MinecraftConfigCommandKeys.HISTORY_TITLE)
                .formatted(Formatting.DARK_GRAY)
                .append(presentationEntryLines(historyStatusEntries(status))),
        ),
    ),
    Formatting.AQUA,
)

/**
 * 只顯示安全的本地化狀態，不把原始例外內容或資料庫路徑送進聊天。
 *
 * @param status writer 的安全狀態。
 * @return 狀態懸停欄位。
 */
private fun historyStatusEntries(status: HistoryWriterStatus): List<ConfigPresentationEntry> = listOf(
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.HISTORY_DATABASE),
        Text.translatable(
            if (status.databaseConnected) MinecraftConfigCommandKeys.HISTORY_CONNECTED else MinecraftConfigCommandKeys.HISTORY_DISCONNECTED,
        ),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.HISTORY_RECORDING),
        Text.translatable(
            if (status.recordingEnabled) MinecraftConfigCommandKeys.HISTORY_RECORDING_ENABLED else MinecraftConfigCommandKeys.HISTORY_RECORDING_DISABLED,
        ),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.HISTORY_STORAGE_STATE),
        Text.translatable(
            if (status.storagePaused) MinecraftConfigCommandKeys.HISTORY_STORAGE_PAUSED else MinecraftConfigCommandKeys.HISTORY_STORAGE_AVAILABLE,
        ),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.HISTORY_PENDING_EVENTS),
        Text.literal(status.pendingEventCount.toString()),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.HISTORY_KNOWN_GAPS),
        Text.literal(status.knownGapCount.toString()),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.HISTORY_ERROR),
        Text.translatable(
            if (status.lastError == null) MinecraftConfigCommandKeys.HISTORY_ERROR_NONE else MinecraftConfigCommandKeys.HISTORY_ERROR_PRESENT,
        ),
    ),
)

/**
 * 建立單則多行的用量訊息，主要數字直接可讀，各分類有獨立短懸停內容。
 *
 * @param snapshot 用量快照。
 * @param stale 是否為非即時快照。
 * @return 可送出的本地化訊息。
 */
internal fun historyStorageMessage(snapshot: HistoryStorageSnapshot, stale: Boolean = false): MutableText = prefixedConfigMessage(
    Text.translatableWithFallback(MinecraftConfigCommandKeys.HISTORY_STORAGE, "History storage"),
    if (stale) Formatting.YELLOW else Formatting.AQUA,
).also { message ->
    val matches = listOf(
        entry(MinecraftConfigCommandKeys.HISTORY_COMPLETED_MATCHES, snapshot.completedMatchCount),
        entry(MinecraftConfigCommandKeys.HISTORY_ACTIVE_MATCHES, snapshot.activeMatchCount),
        entry(MinecraftConfigCommandKeys.HISTORY_PARTIAL_MATCHES, snapshot.partialMatchCount),
        entry(MinecraftConfigCommandKeys.HISTORY_UNKNOWN_MATCHES, snapshot.unknownMatchCount),
    )
    val events = listOf(
        entry(MinecraftConfigCommandKeys.HISTORY_PENDING_SQL_EVENTS, snapshot.pendingSqlEventCount),
        entry(MinecraftConfigCommandKeys.HISTORY_PENDING_OUTBOX_EVENTS, snapshot.pendingOutboxEventCount),
        entry(MinecraftConfigCommandKeys.HISTORY_TOMBSTONES, snapshot.tombstoneCount),
    )
    val disk = listOf(
        entry(MinecraftConfigCommandKeys.HISTORY_DB_BYTES, formatBytes(snapshot.disk.dbBytes)),
        entry(MinecraftConfigCommandKeys.HISTORY_WAL_BYTES, formatBytes(snapshot.disk.walBytes)),
        entry(MinecraftConfigCommandKeys.HISTORY_SHM_BYTES, formatBytes(snapshot.disk.shmBytes)),
    )
    message.appendStorageSection(
        MinecraftConfigCommandKeys.HISTORY_SECTION_MATCHES,
        "Matches",
        Text.translatableWithFallback(
            MinecraftConfigCommandKeys.HISTORY_MATCHES_SUMMARY,
            "Complete %s / active %s / partial %s / unconfirmed %s",
            snapshot.completedMatchCount,
            snapshot.activeMatchCount,
            snapshot.partialMatchCount,
            snapshot.unknownMatchCount,
        ),
        matches,
    ).appendStorageSection(
        MinecraftConfigCommandKeys.HISTORY_SECTION_EVENTS,
        "Events",
        Text.translatableWithFallback(
            MinecraftConfigCommandKeys.HISTORY_EVENTS_SUMMARY,
            "SQL %s / outbox %s / receipts %s",
            snapshot.pendingSqlEventCount,
            snapshot.pendingOutboxEventCount,
            snapshot.tombstoneCount,
        ),
        events,
    ).appendStorageSection(
        MinecraftConfigCommandKeys.HISTORY_SECTION_DISK,
        "Disk",
        Text.literal(formatDiskUsage(snapshot.disk)),
        disk,
    ).appendStorageSection(
        MinecraftConfigCommandKeys.HISTORY_SECTION_LIMITS,
        "Retention limits",
        Text.translatableWithFallback(
            MinecraftConfigCommandKeys.HISTORY_LIMITS_SUMMARY,
            "Matches: %s / days: %s / disk: %s",
            limitText(snapshot.policy.maxMatches.toLong()),
            limitText(snapshot.policy.retentionDuration.inWholeDays),
            formatBytes(snapshot.policy.maxDiskBytes),
        ),
        policyEntries(snapshot.policy),
    )
    message.append(Text.literal("\n  ")).append(Text.translatable(MinecraftConfigCommandKeys.HISTORY_UPDATED_AT))
        .append(": ").append(snapshot.updatedAt.toString())
    if (stale) message.append(Text.literal("\n  ")).append(Text.translatable(MinecraftConfigCommandKeys.HISTORY_STALE).formatted(Formatting.YELLOW))
}

/**
 * 建立清理預覽訊息，分開確定候選與容量階段的可能候選。
 *
 * @param preview 清理預覽。
 * @return 可送出的本地化訊息。
 */
internal fun historyCleanupPreviewMessage(preview: HistoryCleanupPreview): MutableText = prefixedConfigMessage(
    Text.translatable(
        MinecraftConfigCommandKeys.HISTORY_CLEANUP_PREVIEW,
        bracketedInteractiveLabel(
            Text.translatable(MinecraftConfigCommandKeys.DETAILS),
            Text.translatable(MinecraftConfigCommandKeys.HISTORY_CLEANUP_PREVIEW_TITLE)
                .formatted(Formatting.DARK_GRAY)
                .append(presentationEntryLines(historyCleanupPreviewEntries(preview))),
        ),
    ),
    Formatting.AQUA,
).also { message ->
    message.append(Text.literal("\n  • ").formatted(Formatting.GRAY))
        .append(Text.translatable(MinecraftConfigCommandKeys.HISTORY_CANDIDATE_MATCHES).formatted(Formatting.GRAY))
        .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
        .append(Text.literal(preview.countsByReason.values.sum().toString()).formatted(Formatting.GREEN))
        .append(Text.literal("\n  • ").formatted(Formatting.GRAY))
        .append(Text.translatable(MinecraftConfigCommandKeys.HISTORY_LOGICAL_BYTES).formatted(Formatting.GRAY))
        .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
        .append(Text.literal(formatBytes(preview.logicalBytes)).formatted(Formatting.GREEN))
        .append(Text.literal("\n  • ").formatted(Formatting.GRAY))
        .append(Text.translatable(MinecraftConfigCommandKeys.HISTORY_ADDITIONAL_CANDIDATES).formatted(Formatting.GRAY))
        .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
        .append(Text.literal(preview.additionalCandidateCount.toString()).formatted(Formatting.YELLOW))
}

/**
 * 建立清理執行報告，不將負的淨磁碟變化截成零。
 *
 * @param report 清理執行報告。
 * @return 可送出的本地化訊息。
 */
internal fun historyCleanupReportMessage(report: HistoryCleanupReport): MutableText = prefixedConfigMessage(
    Text.translatable(
        MinecraftConfigCommandKeys.HISTORY_CLEANUP_COMPLETE,
        bracketedInteractiveLabel(
            Text.translatable(MinecraftConfigCommandKeys.DETAILS),
            Text.translatable(MinecraftConfigCommandKeys.HISTORY_CLEANUP_REPORT_TITLE)
                .formatted(Formatting.DARK_GRAY)
                .append(presentationEntryLines(historyCleanupReportEntries(report))),
        ),
    ),
    if (report.completed) Formatting.GREEN else Formatting.YELLOW,
).also { message ->
    message.append(Text.literal("\n  • ").formatted(Formatting.GRAY))
        .append(Text.translatable(MinecraftConfigCommandKeys.HISTORY_REMOVED_MATCHES).formatted(Formatting.GRAY))
        .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
        .append(Text.literal(report.removedMatches.toString()).formatted(Formatting.GREEN))
        .append(Text.literal("\n  • ").formatted(Formatting.GRAY))
        .append(Text.translatable(MinecraftConfigCommandKeys.HISTORY_NET_DISK_CHANGE).formatted(Formatting.GRAY))
        .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
        .append(Text.literal(formatSignedBytes(netDiskChange(report.diskBefore, report.diskAfter))))
        .append(Text.literal("\n  • ").formatted(Formatting.GRAY))
        .append(Text.translatable(MinecraftConfigCommandKeys.HISTORY_RESULT_STATE).formatted(Formatting.GRAY))
        .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
        .append(Text.translatable(if (report.completed) MinecraftConfigCommandKeys.HISTORY_RESULT_COMPLETED else MinecraftConfigCommandKeys.HISTORY_RESULT_PARTIAL))
}

/**
 * 建立可見的分類摘要，只有分類標籤持有懸停內容。
 *
 * @param key 分類翻譯鍵。
 * @param fallback console 缺少模組語系時使用的英文分類名稱。
 * @param summary 不依賴懸停也可讀的摘要。
 * @param entries 此分類的詳細欄位。
 * @return 可加入主訊息的分類列。
 */
private fun MutableText.appendStorageSection(key: String, fallback: String, summary: Text, entries: List<ConfigPresentationEntry>): MutableText = append(Text.literal("\n  • "))
    .append(bracketedInteractiveLabel(Text.translatableWithFallback(key, fallback), Text.translatable(key).append(presentationEntryLines(entries))))
    .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
    .append(summary.copy().formatted(Formatting.GRAY))

/**
 * 建立安全的 literal 值欄位，不加入玩家或牌面資料。
 *
 * @param key 欄位翻譯鍵。
 * @param value 可直接顯示的數字或已格式化字串。
 * @return 本地化欄位。
 */
private fun entry(key: String, value: Any): ConfigPresentationEntry = ConfigPresentationEntry(Text.translatable(key), Text.literal(value.toString()))

/**
 * 將零上限顯示為不限。
 *
 * @param value 場數或天數上限。
 * @return 本地化的上限。
 */
private fun limitText(value: Long): Text = if (value == 0L) Text.translatableWithFallback(MinecraftConfigCommandKeys.HISTORY_UNLIMITED, "unlimited") else Text.literal(value.toString())

/**
 * 建立有效保留政策的短欄位列表。
 *
 * @param policy 固定的有效政策。
 * @return 場數、期限、磁碟及部分紀錄政策。
 */
private fun policyEntries(policy: HistoryRetentionPolicy): List<ConfigPresentationEntry> = listOf(
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_MAX_MATCHES), limitText(policy.maxMatches.toLong())),
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_RETENTION_DAYS), limitText(policy.retentionDuration.inWholeDays)),
    entry(MinecraftConfigCommandKeys.HISTORY_MAX_DISK_MIB, formatBytes(policy.maxDiskBytes)),
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_INCLUDE_INTERRUPTED_MATCHES), booleanText(policy.includeInterruptedMatches)),
)

/**
 * 將清理預覽轉為原因、邏輯資料量與可能追加候選的欄位。
 *
 * @param preview 唯讀預覽。
 * @return 不包含場次 ID 的欄位。
 */
private fun historyCleanupPreviewEntries(preview: HistoryCleanupPreview): List<ConfigPresentationEntry> = buildList {
    preview.countsByReason.forEach { (reason, count) ->
        add(ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_CLEANUP_REASON), Text.translatable(cleanupReasonKey(reason)).append(" ").append(count.toString())))
    }
    add(ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_LOGICAL_BYTES), Text.literal(formatBytes(preview.logicalBytes))))
    add(ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_ADDITIONAL_CANDIDATES), Text.literal(preview.additionalCandidateCount.toString())))
    add(ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_ADDITIONAL_LOGICAL_BYTES), Text.literal(formatBytes(preview.additionalLogicalBytes))))
    add(ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_DISK_USAGE), Text.literal(formatDiskUsage(preview.disk))))
    add(ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_EVALUATED_AT), Text.literal(preview.evaluatedAt.toString())))
    add(ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_NO_EXACT_DISK_PROMISE), Text.translatable(MinecraftConfigCommandKeys.HISTORY_NO_EXACT_DISK_PROMISE)))
}

/**
 * 將清理報告轉為實際提交刪除與回收狀態欄位。
 *
 * @param report 已完成或部分完成的清理結果。
 * @return 實際磁碟與回收狀態欄位。
 */
private fun historyCleanupReportEntries(report: HistoryCleanupReport): List<ConfigPresentationEntry> = listOf(
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_REMOVED_MATCHES), Text.literal(report.removedMatches.toString())),
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_DISK_BEFORE), Text.literal(report.diskBefore?.let(::formatDiskUsage) ?: "—")),
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_DISK_AFTER), Text.literal(report.diskAfter?.let(::formatDiskUsage) ?: "—")),
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_NET_DISK_CHANGE), Text.literal(formatSignedBytes(netDiskChange(report.diskBefore, report.diskAfter)))),
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_STORAGE_AVAILABLE_RESULT), booleanText(report.storageAvailable)),
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_RECOVERY_BUSY), booleanText(report.recoveryBusy)),
    ConfigPresentationEntry(Text.translatable(MinecraftConfigCommandKeys.HISTORY_INCREMENTAL_SUPPORTED), booleanText(report.incrementalSupported)),
)

/**
 * 將三態結果轉為本地化文字，未知不當作否。
 *
 * @param value 結果；null 表示尚未取得。
 * @return 是、否或未知的文字。
 */
private fun booleanText(value: Boolean?): Text = Text.translatable(
    when (value) {
        true -> MinecraftConfigCommandKeys.HISTORY_YES
        false -> MinecraftConfigCommandKeys.HISTORY_NO
        null -> MinecraftConfigCommandKeys.HISTORY_UNKNOWN
    },
)

/**
 * 將安全磁碟統計同時格式化為 MiB 與原始位元組。
 *
 * @param disk 三檔的實際大小。
 * @return 磁碟加總文字。
 */
private fun formatDiskUsage(disk: HistoryDiskUsage): String = "${formatMiB(disk.totalBytes)} (${disk.totalBytes} bytes)"

/**
 * 將位元組格式化為固定小數的 MiB。
 *
 * @param bytes 位元組大小。
 * @return 不受系統語系影響的小數文字。
 */
private fun formatMiB(bytes: Long): String = "%.2f MiB".format(Locale.ROOT, bytes / (1024.0 * 1024.0))

/**
 * 將位元組格式化為 MiB 與原始 bytes。
 *
 * @param bytes 位元組大小。
 * @return 包含原始精度的文字。
 */
private fun formatBytes(bytes: Long): String = "${formatMiB(bytes)} ($bytes bytes)"

/**
 * 計算前後磁碟大小的有號差值；正值表示增加。
 *
 * @param before 執行前用量，可能尚未量得。
 * @param after 執行後用量，可能量測失敗。
 * @return 差值；缺少量測時為 null。
 */
private fun netDiskChange(before: HistoryDiskUsage?, after: HistoryDiskUsage?): Long? = if (before == null || after == null) null else after.totalBytes - before.totalBytes

/**
 * 將有號磁碟差值格式化，不掩蓋 WAL 造成的增長。
 *
 * @param value 有號差值；null 為未知。
 * @return 有號的大小文字或未知符號。
 */
private fun formatSignedBytes(value: Long?): String = value?.let { "${if (it >= 0) "+" else ""}${formatBytes(it)}" } ?: "—"

/**
 * 取得清理原因的翻譯鍵。
 *
 * @param reason 政策評估的主要原因。
 * @return 四語共同的翻譯鍵。
 */
private fun cleanupReasonKey(reason: HistoryCleanupReason): String = when (reason) {
    HistoryCleanupReason.INTERRUPTED_EXCLUDED -> MinecraftConfigCommandKeys.HISTORY_REASON_INTERRUPTED
    HistoryCleanupReason.EXPIRED -> MinecraftConfigCommandKeys.HISTORY_REASON_EXPIRED
    HistoryCleanupReason.MATCH_LIMIT -> MinecraftConfigCommandKeys.HISTORY_REASON_MATCH_LIMIT
    HistoryCleanupReason.DISK_LIMIT -> MinecraftConfigCommandKeys.HISTORY_REASON_DISK_LIMIT
}
