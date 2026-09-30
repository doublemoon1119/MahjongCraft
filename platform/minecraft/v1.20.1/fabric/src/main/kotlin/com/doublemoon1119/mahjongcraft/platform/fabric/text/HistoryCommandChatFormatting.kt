package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/** 建立與 config show 相同前綴、單行詳情標籤的歷史狀態訊息。 */
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

/** 只顯示安全的本地化狀態，不把原始例外內容或資料庫路徑送進聊天。 */
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
