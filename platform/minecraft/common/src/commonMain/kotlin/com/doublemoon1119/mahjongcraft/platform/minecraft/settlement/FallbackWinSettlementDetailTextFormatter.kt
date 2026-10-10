package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity

/** 沒有登記格式化器的胡牌詳情 fallback。 */
object FallbackWinSettlementDetailTextFormatter : WinSettlementDetailTextFormatter {
    override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = PresentationValue.TextValue(
        quantities.joinToString(" ") { "${it.amount} ${it.unitId}" },
    )

    override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = PresentationValue.EntryListValue(
        entries.map { entry ->
            PresentationValue.EntryListValue.Entry(
                translationKey = entry.id,
                trailingText = entry.quantity?.let { "${it.amount} ${it.unitId}" }.orEmpty(),
            )
        },
    )
}
