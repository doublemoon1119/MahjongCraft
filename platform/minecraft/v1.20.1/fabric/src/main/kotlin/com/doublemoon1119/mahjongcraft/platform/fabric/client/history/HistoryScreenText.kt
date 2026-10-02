package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import net.minecraft.text.Text
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 歷史列表的安全文字格式化工具；未知值一律保留為未知，不補造資料。 */
internal object HistoryScreenText {
    /** 將單場保存狀態轉為畫面提示；已保存狀態不額外佔用列表空間。
     *
     * @param value 目前保存狀態。
     * @return 應顯示的提示文字；無需提示時為 null。
     */
    fun archiveStatus(value: HistoryArchiveStatusView): Text? = when (value) {
        HistoryArchiveStatusView.Idle -> null
        is HistoryArchiveStatusView.Resolved -> if (value.status == HistoryArchiveStatusDto.SAVED) {
            null
        } else {
            Text.translatable("${MinecraftHistoryScreenKeys.ARCHIVE_STATUS_PREFIX}${value.status.name.lowercase()}")
        }
        is HistoryArchiveStatusView.Pending -> Text.translatable("${MinecraftHistoryScreenKeys.ARCHIVE_STATUS_PREFIX}pending")
        is HistoryArchiveStatusView.Failed -> Text.translatable(MinecraftHistoryScreenKeys.ARCHIVE_UNAVAILABLE)
    }

    /** 將對局結束時間格式化為本地時間。
     * @param value Unix epoch 毫秒；null 表示未知。
     * @param zone 顯示使用的時區。
     * @return 格式化後的日期時間。
     */
    fun endedAt(value: Long?, zone: ZoneId = ZoneId.systemDefault()): String = value?.let {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(it).atZone(zone))
    } ?: "—"

    /** 將毫秒時長格式化為分秒。
     * @param value 毫秒時長；負值或 null 表示未知。
     * @return 格式化後的分秒文字。
     */
    fun duration(value: Long?): String = value?.takeIf { it >= 0 }?.let {
        val duration = Duration.ofMillis(it)
        "%dm %02ds".format(duration.toMinutes(), duration.toSecondsPart())
    } ?: "—"

    /** 將結果狀態轉為可翻譯文字。
     * @param value 對局結果狀態。
     * @return 對應的翻譯文字。
     */
    fun outcome(value: HistoryOutcomeFilterDto?): Text = when (value) {
        HistoryOutcomeFilterDto.COMPLETED -> Text.translatable(MinecraftHistoryScreenKeys.OUTCOME_COMPLETED)
        HistoryOutcomeFilterDto.INTERRUPTED -> Text.translatable(MinecraftHistoryScreenKeys.OUTCOME_INTERRUPTED)
        null -> Text.translatable(MinecraftHistoryScreenKeys.UNKNOWN)
    }

    /** 將規則 ID 轉成 registry 顯示名稱，未註冊時保留完整 ID。
     * @param value 規則識別碼。
     * @param registry 規則顯示名稱 registry。
     * @return 規則顯示文字。
     */
    fun rule(value: String?, registry: RuleModuleDisplayNameRegistry): Text = value?.let { id ->
        registry.find(id)?.let(Text::translatable) ?: Text.literal(id)
    } ?: Text.translatable(MinecraftHistoryScreenKeys.UNKNOWN)

    /** 從結果中只取指定玩家的名次與分數。
     * @param summary 對局摘要。
     * @param playerId 指定玩家識別碼。
     * @return 名次與分數；找不到時各自為 null。
     */
    fun ownResult(summary: HistoryMatchSummaryDto, playerId: String?): Pair<Int?, Int?> {
        val result = playerId?.let { id -> summary.results.firstOrNull { it.playerId == id } }
        return result?.finalRank to result?.finalScore
    }

    /**
     * 按權威最終名次排列參與者，同名次與未知名次以座位穩定排序。
     *
     * @param summary 已保存的對局摘要。
     * @return 不以分數推測名次的參與者清單。
     */
    fun rankedParticipants(summary: HistoryMatchSummaryDto): List<HistoryParticipantSummaryDto> {
        val ranks = summary.results.associate { it.playerId to it.finalRank }
        return summary.participants.sortedWith(compareBy<HistoryParticipantSummaryDto> { ranks[it.playerId] ?: Int.MAX_VALUE }.thenBy { it.seatIndex })
    }

    /** 將可能過長的文字裁切為指定像素寬度。
     * @param text 原始文字。
     * @param maxWidth 最大像素寬度。
     * @param width 文字寬度測量函式。
     * @return 符合寬度的文字。
     */
    fun trim(text: String, maxWidth: Int, width: (String) -> Int): String {
        if (maxWidth <= 0 || width(text) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && width("$value…") > maxWidth) value = value.dropLast(1)
        return if (value.isEmpty()) "…" else "$value…"
    }
}
