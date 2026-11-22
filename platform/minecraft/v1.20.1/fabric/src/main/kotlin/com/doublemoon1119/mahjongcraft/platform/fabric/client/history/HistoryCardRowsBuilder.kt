package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryCardStyle
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component

/**
 * 依卡片寬度逐列排出牌譜卡片內容。
 *
 * @property textRenderer 換行用的文字繪製器。
 * @property contentWidth 卡片內容寬度。
 */
internal class HistoryCardRowsBuilder(
    private val textRenderer: Font,
    val contentWidth: Int,
) {
    /** 已排好的列。 */
    val rows: MutableList<HistoryCardRow> = mutableListOf()

    /**
     * 加入換行文字，頭像僅放於第一列。
     *
     * @param text 完整文字及 tooltip。
     * @param color 文字色。
     * @param indent 卡片內縮排。
     * @param participant 可選頭像來源。
     * @param identifiers 僅供 tooltip 查閱的原始識別碼。
     */
    fun addText(
        text: Component,
        color: Int = HistoryCardStyle.TEXT_COLOR,
        indent: Int = 8,
        participant: HistoryParticipantSummaryDto? = null,
        identifiers: List<String> = emptyList(),
    ) {
        val left = indent + if (participant != null) HistoryCardStyle.PORTRAIT_STEP else 0
        textRenderer.split(text, (contentWidth - left - 8).coerceAtLeast(1)).forEachIndexed { index, line ->
            rows += HistoryCardRow.TextLine(line, listOf(text) + identifiers.map { Component.literal(it).withStyle(ChatFormatting.GRAY) }, left, color, participant.takeIf { index == 0 }, indent)
        }
    }

    /**
     * 一列放得下幾張牌。
     *
     * @param indent 牌面縮排。
     * @return 至少一張。
     */
    fun tilesPerRow(indent: Int): Int = ((contentWidth - indent - 8) / HistoryCardStyle.TILE_STEP).coerceAtLeast(1)
}
