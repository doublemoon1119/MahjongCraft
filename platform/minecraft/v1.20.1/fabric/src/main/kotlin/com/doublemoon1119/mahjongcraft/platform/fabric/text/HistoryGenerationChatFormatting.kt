package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.HistoryGenerationFailure
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.HistoryGenerationProgress
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryDiskUsage
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.HistoryDebugKeys
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.Locale

/**
 * 建立正式歷史生成 debug 批次的單則進度訊息。
 *
 * @param progress 不包含玩家與牌面的批次進度。
 * @return 具有單一 prefix 與條列內容的訊息。
 */
internal fun historyGenerationProgressMessage(progress: HistoryGenerationProgress): MutableText = prefixedConfigMessage(
    label(HistoryDebugKeys.PROGRESS),
    if (progress.failure == null && progress.running) Formatting.AQUA else Formatting.YELLOW,
).also { message ->
    message.append(generationEntry(HistoryDebugKeys.SCENARIO, Text.literal(progress.scenarioId), Formatting.GRAY))
        .append(generationEntry(HistoryDebugKeys.REQUESTED, progress.requested, Formatting.GREEN))
        .append(generationEntry(HistoryDebugKeys.GENERATED, progress.generated, Formatting.GREEN))
        .append(generationEntry(HistoryDebugKeys.ARCHIVED, progress.archived, Formatting.GREEN))
        .append(generationEntry(HistoryDebugKeys.PRUNED, progress.pruned, Formatting.YELLOW))
        .append(generationEntry(HistoryDebugKeys.PENDING, progress.pending, Formatting.YELLOW))
        .append(generationEntry(HistoryDebugKeys.FAILED, progress.failed, Formatting.YELLOW))
        .append(generationEntry(HistoryDebugKeys.ELAPSED_SECONDS, formatSeconds(progress.elapsed.inWholeMilliseconds), Formatting.GRAY))
        .append(generationEntry(HistoryDebugKeys.DISK_BEFORE, formatDisk(progress.diskBefore), Formatting.GRAY))
        .append(generationEntry(HistoryDebugKeys.DISK_AFTER, formatDisk(progress.diskAfter), Formatting.GRAY))
        .append(generationEntry(HistoryDebugKeys.REPLAY_TOTAL, formatBytes(progress.replayBytes), Formatting.GRAY))
        .append(generationEntry(HistoryDebugKeys.REPLAY_AVERAGE, formatBytes(averageReplayBytes(progress)), Formatting.GRAY))
        .append(generationEntry(HistoryDebugKeys.REPLAY_MAXIMUM, formatBytes(progress.largestReplayBytes), Formatting.GRAY))
        .append(
            generationEntry(
                HistoryDebugKeys.CANCEL_STATE,
                label(
                    if (progress.cancelRequested) {
                        HistoryDebugKeys.CANCEL_REQUESTED
                    } else if (progress.running) {
                        HistoryDebugKeys.RUNNING
                    } else {
                        HistoryDebugKeys.COMPLETED
                    },
                ),
                Formatting.YELLOW,
            ),
        )
    progress.failure?.let { failure ->
        message.append(generationEntry(HistoryDebugKeys.FAILED, label(failureKey(failure)), Formatting.YELLOW))
    } ?: message.append(generationEntry(HistoryDebugKeys.PROGRESS, label(if (progress.running) HistoryDebugKeys.RUNNING else HistoryDebugKeys.COMPLETED), Formatting.GRAY))
}

/**
 * 建立單一灰色欄位標籤與指定顏色值。
 *
 * @param key 欄位翻譯鍵。
 * @param value 欄位值。
 * @param valueColor 值的顯示顏色。
 * @return 可加入進度訊息的欄位列。
 */
private fun generationEntry(key: String, value: Any, valueColor: Formatting): MutableText = Text.literal("\n  • ")
    .formatted(Formatting.GRAY)
    .append(label(key).formatted(Formatting.GRAY))
    .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
    .append((value as? Text)?.copy()?.formatted(valueColor) ?: Text.literal(value.toString()).formatted(valueColor))

/**
 * 建立具備英文 console fallback 的欄位文字。
 *
 * @param key 欄位翻譯鍵。
 * @return 帶 fallback 的本地化文字。
 */
private fun label(key: String): MutableText = Text.translatableWithFallback(key, fallbackLabel(key))

/**
 * 依穩定翻譯鍵提供不依賴語言檔的英文欄位名稱。
 *
 * @param key 欄位翻譯鍵。
 * @return console 使用的英文名稱。
 */
private fun fallbackLabel(key: String): String = when (key) {
    HistoryDebugKeys.PROGRESS -> "History generation progress"
    HistoryDebugKeys.SCENARIO -> "Scenario"
    HistoryDebugKeys.REQUESTED -> "Requested"
    HistoryDebugKeys.GENERATED -> "Generated"
    HistoryDebugKeys.ARCHIVED -> "Archived"
    HistoryDebugKeys.PRUNED -> "Pruned"
    HistoryDebugKeys.PENDING -> "Pending"
    HistoryDebugKeys.FAILED -> "Failed"
    HistoryDebugKeys.ELAPSED_SECONDS -> "Elapsed seconds"
    HistoryDebugKeys.CANCEL_STATE -> "Cancel state"
    HistoryDebugKeys.DISK_BEFORE -> "Disk before"
    HistoryDebugKeys.DISK_AFTER -> "Disk after"
    HistoryDebugKeys.REPLAY_TOTAL -> "Replay total"
    HistoryDebugKeys.REPLAY_AVERAGE -> "Replay average"
    HistoryDebugKeys.REPLAY_MAXIMUM -> "Replay maximum"
    HistoryDebugKeys.CANCEL_REQUESTED -> "cancel requested"
    HistoryDebugKeys.RUNNING -> "running"
    HistoryDebugKeys.COMPLETED -> "completed"
    HistoryDebugKeys.FAILURE_POLICY -> "Policy rejected"
    HistoryDebugKeys.FAILURE_STORAGE -> "Storage unavailable"
    HistoryDebugKeys.FAILURE_TIMEOUT -> "Generation timed out"
    HistoryDebugKeys.FAILURE_SESSION -> "History session changed"
    HistoryDebugKeys.FAILURE_VALIDATION -> "Validation failed"
    else -> key
}

/**
 * 將單調經過時間格式化為秒數。
 *
 * @param milliseconds 已經過的毫秒數。
 * @return 固定一位小數的秒數。
 */
private fun formatSeconds(milliseconds: Long): String = "%.1f".format(Locale.ROOT, milliseconds / 1000.0)

/**
 * 將磁碟用量格式化為安全的 MiB 與 bytes；未知量不以零取代。
 *
 * @param disk 三檔實際磁碟用量，或尚未量測的 null。
 * @return 格式化總量或未知標記。
 */
private fun formatDisk(disk: HistoryDiskUsage?): String = disk?.let { formatBytes(it.totalBytes) } ?: "—"

/**
 * 將 Replay 位元組格式化為安全的 MiB 與 bytes。
 *
 * @param bytes 實際位元組數。
 * @return 同時包含 MiB 與完整 bytes 的文字。
 */
private fun formatBytes(bytes: Long): String = "%.2f MiB (%d bytes)".format(Locale.ROOT, bytes / (1024.0 * 1024.0), bytes)

/**
 * 以實際量測的 Replay 樣本數計算平均資料量。
 *
 * @param progress 目前生成進度。
 * @return 每個已量測 Replay 的平均位元組數。
 */
private fun averageReplayBytes(progress: HistoryGenerationProgress): Long = if (progress.replaySampleCount == 0) 0L else progress.replayBytes / progress.replaySampleCount

/**
 * 將失敗分類映射為穩定翻譯鍵。
 *
 * @param failure 不含例外原文的安全分類。
 * @return 對應的翻譯鍵。
 */
private fun failureKey(failure: HistoryGenerationFailure): String = when (failure) {
    HistoryGenerationFailure.POLICY -> HistoryDebugKeys.FAILURE_POLICY
    HistoryGenerationFailure.STORAGE -> HistoryDebugKeys.FAILURE_STORAGE
    HistoryGenerationFailure.TIMEOUT -> HistoryDebugKeys.FAILURE_TIMEOUT
    HistoryGenerationFailure.SESSION -> HistoryDebugKeys.FAILURE_SESSION
    HistoryGenerationFailure.VALIDATION -> HistoryDebugKeys.FAILURE_VALIDATION
}
