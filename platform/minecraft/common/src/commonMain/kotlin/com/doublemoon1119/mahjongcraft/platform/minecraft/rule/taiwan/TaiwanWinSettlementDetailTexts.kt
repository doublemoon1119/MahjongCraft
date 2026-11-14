package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.EntryEmphasis
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.FallbackWinSettlementDetailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatter

/** 台灣麻將胡牌詳情的顯示文字。 */
internal object TaiwanWinSettlementDetailTexts {
    /** 以強調樣式顯示的最低台數。 */
    const val HIGHLIGHT_TAI: Int = 8

    /**
     * 將台種 ID 與台數轉成玩家可讀文字。
     *
     * 只適用於部分付款者的條目（例如閒家自摸時的莊家台）以對應樣式顯示；其餘 [HIGHLIGHT_TAI] 台以上的台種以強調樣式顯示。
     */
    val taiText: WinSettlementDetailTextFormatter = object : WinSettlementDetailTextFormatter {
        override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = FallbackWinSettlementDetailTextFormatter.quantities(quantities)

        override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = PresentationValue.EntryListValue(
            entries = entries.map { entry ->
                PresentationValue.EntryListValue.Entry(
                    translationKey = TaiwanTaiTranslationKeys.keyForEntry(entry.id) ?: entry.id,
                    translationArgument = entry.nameQuantity?.amount?.toString(),
                    trailingTranslationKey = TaiwanTranslationKeys.SETTLEMENT_TAI_COUNT,
                    trailingTranslationArgument = entry.quantity?.amount?.toString(),
                    emphasis = when {
                        entry.appliesToSomePayersOnly -> EntryEmphasis.CONDITIONAL
                        entry.quantity?.let { it.unitId == TaiwanWinSettlementIds.TAI_UNIT && it.amount >= HIGHLIGHT_TAI } == true -> EntryEmphasis.HIGHLIGHTED
                        else -> EntryEmphasis.NORMAL
                    },
                )
            },
        )
    }

    /** 閒家自摸時莊家那一份的合計：「（莊家 N 台）」。 */
    val dealerTaiTotalText: WinSettlementDetailTextFormatter = object : WinSettlementDetailTextFormatter {
        override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = quantities.singleOrNull()
            ?.takeIf { it.unitId == TaiwanWinSettlementIds.TAI_UNIT }
            ?.let { PresentationValue.TextValue(TaiwanTranslationKeys.SETTLEMENT_DEALER_TAI_TOTAL, listOf(it.amount.toString())) }
            ?: FallbackWinSettlementDetailTextFormatter.quantities(quantities)

        override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = FallbackWinSettlementDetailTextFormatter.entries(entries)
    }

    /** 合計台數：「N 台」。 */
    val taiTotalText: WinSettlementDetailTextFormatter = object : WinSettlementDetailTextFormatter {
        override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = quantities.singleOrNull()
            ?.takeIf { it.unitId == TaiwanWinSettlementIds.TAI_UNIT }
            ?.let { PresentationValue.TextValue(TaiwanTranslationKeys.SETTLEMENT_TAI_COUNT, listOf(it.amount.toString())) }
            ?: FallbackWinSettlementDetailTextFormatter.quantities(quantities)

        override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = FallbackWinSettlementDetailTextFormatter.entries(entries)
    }
}
