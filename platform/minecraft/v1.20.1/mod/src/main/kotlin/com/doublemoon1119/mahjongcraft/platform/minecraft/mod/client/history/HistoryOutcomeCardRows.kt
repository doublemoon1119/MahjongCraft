package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileSetAsideDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import net.minecraft.network.chat.Component

/**
 * 一筆保存結算在牌譜卡片中的各列：結算原因與分類、各家分數與變化，贏家的和牌手牌、副露與規則明細。事件頁的交易卡片與
 * 回放的結算卡片共用。
 *
 * @property eventPresenter 已驗證事件的呈現 helper。
 * @property settlementTemplates 明細欄位標題的規則模板來源。
 * @property setAsideDisplays 移出牌區的標題來源。
 */
internal class HistoryOutcomeCardRows(
    private val eventPresenter: HistoryRoundEventPresenter,
    private val settlementTemplates: WinSettlementPresentationTemplateRegistry,
    private val setAsideDisplays: HistoryTileSetAsideDisplayRegistry,
) {
    /**
     * 把一筆結算加進卡片。
     *
     * @param builder 卡片內容。
     * @param outcome 結算的呈現內容。
     * @param savedOutcome 保存的結算原文，提供贏家的和牌手牌；沒有時為 null。
     * @param ruleId 這場對局的規則。
     * @param transactionIndex 這筆結算所在的交易索引，供讀取和牌手牌的按鈕使用。
     * @param actorText 依初始座位取得玩家名稱。
     * @param participant 依初始座位取得頭像來源。
     * @param winningHand 畫出和牌手牌所需的牌面。
     */
    fun add(
        builder: HistoryCardRowsBuilder,
        outcome: HistoryOutcomePresentation,
        savedOutcome: HistoryRoundOutcomeDto?,
        ruleId: String?,
        transactionIndex: Int,
        actorText: (Int) -> Component,
        participant: (Int) -> HistoryParticipantSummaryDto?,
        winningHand: () -> HistoryWinningHandSource,
    ) {
        val rows = builder.rows
        builder.addText(Component.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_REASON, outcome.reasonText), HistoryCardStyle.ACCENT_COLOR, 24, identifiers = listOf(outcome.reasonId))
        outcome.classification?.let {
            builder.addText(Component.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_CLASSIFICATION, eventPresenter.classificationText(it)), HistoryCardStyle.MUTED_COLOR, 24, identifiers = listOf(it))
        }
        outcome.rows.forEach { row ->
            val score = Component.translatable(MinecraftHistoryScreenKeys.ROUND_SCORE, actorText(row.seatIndex), row.score?.toString() ?: "—")
            row.scoreChange?.let { score.append(" (").append(eventPresenter.scoreChangeText(it)).append(")") }
            eventPresenter.settlementStatusText(outcome, row)?.let { score.append(" • ").append(it) }
            if (row.responsible) score.append(" • ").append(Component.translatable(MinecraftHistoryScreenKeys.ROUND_RESPONSIBLE))
            val scoreColor = if (row.beneficiary && outcome.classification != RoundCompletionClassification.EXHAUSTIVE_DRAW.name) HistoryCardStyle.ACCENT_COLOR else HistoryCardStyle.TEXT_COLOR
            builder.addText(score, scoreColor, 24, participant(row.seatIndex))
            savedOutcome?.winnerDetails?.firstOrNull { it.seatIndex == row.seatIndex }?.let { winner ->
                builder.addText(Component.translatable(MinecraftHistoryScreenKeys.STATE_WINNING_HAND), HistoryCardStyle.MUTED_COLOR, 38)
                val source = winningHand()
                val state = (source as? HistoryWinningHandSource.Ready)?.state
                val handWidth = (builder.contentWidth - HistoryCardStyle.HAND_INDENT - 8).coerceAtLeast(1).toFloat()
                val hand = state?.let { HistoryRoundStatePresenter.winningHand(it, winner, handWidth) }
                when {
                    winner.hand == null -> builder.addText(Component.translatable(MinecraftHistoryScreenKeys.STATE_WINNING_HAND_UNAVAILABLE), HistoryCardStyle.MUTED_COLOR, 46)
                    hand != null -> {
                        rows += HistoryCardRow.Hand(hand, state.tileCatalog)
                        val winnerState = state.players.firstOrNull { it.initialSeatIndex == winner.seatIndex }
                        winnerState?.let { HistoryRoundStatePresenter.exposedTiles(state, it, handWidth) }?.let { exposed ->
                            builder.addText(exposed.heading(setAsideDisplays::findAreaName), HistoryCardStyle.MUTED_COLOR, 38)
                            rows += HistoryCardRow.Hand(exposed.layout, state.tileCatalog, exposed.notes(actorText))
                        }
                    }
                    state != null -> builder.addText(Component.translatable(MinecraftHistoryScreenKeys.STATE_WINNING_HAND_UNAVAILABLE), HistoryCardStyle.MUTED_COLOR, 46)
                    source == HistoryWinningHandSource.Loading -> builder.addText(Component.translatable(MinecraftHistoryScreenKeys.LOADING), HistoryCardStyle.MUTED_COLOR, 46)
                    source is HistoryWinningHandSource.Failed -> {
                        builder.addText(Component.translatable(source.reason.translationKey), HistoryCardStyle.ERROR_COLOR, 46)
                        rows += HistoryCardRow.StateLink(transactionIndex)
                    }
                    else -> rows += HistoryCardRow.StateLink(transactionIndex)
                }
            }
            if (row.detailFields.isNotEmpty()) {
                builder.addText(Component.translatable(MinecraftHistoryScreenKeys.ROUND_WIN_DETAILS), HistoryCardStyle.MUTED_COLOR, 38)
                row.detailFields.forEach detailField@{ field ->
                    val tileValue = field.value as? HistoryWinDetailValueDto.TileFaces
                    if (tileValue != null && tileValue.faces.isEmpty()) return@detailField
                    // 只列贏家自己公開牌的欄位，展開的和牌區已在副露旁畫出這些牌。
                    if (tileValue != null && eventPresenter.isWinnerTileDetailField(ruleId, field.id, settlementTemplates)) return@detailField
                    eventPresenter.detailLabel(ruleId, field.id, settlementTemplates)?.let {
                        builder.addText(it, HistoryCardStyle.MUTED_COLOR, 46, identifiers = listOf(field.id))
                    }
                    eventPresenter.detailText(field.id, field.value, settlementTemplates).forEach { builder.addText(it, HistoryCardStyle.TEXT_COLOR, 46, identifiers = listOf(field.id)) }
                    val faces = when (val value = field.value) {
                        is HistoryWinDetailValueDto.TileFaces -> value.faces
                        else -> emptyList()
                    }
                    faces.takeIf { it.isNotEmpty() }?.chunked(builder.tilesPerRow(HistoryCardStyle.HAND_INDENT))?.forEach { rows += HistoryCardRow.Tiles(it, HistoryCardStyle.HAND_INDENT) }
                }
            } else if (row.beneficiary && outcome.classification == RoundCompletionClassification.WIN.name && !outcome.hasEarlierWinSettlement) {
                builder.addText(Component.translatable(MinecraftHistoryScreenKeys.ROUND_WIN_DETAILS_UNAVAILABLE), HistoryCardStyle.MUTED_COLOR, 38)
            }
        }
    }
}
