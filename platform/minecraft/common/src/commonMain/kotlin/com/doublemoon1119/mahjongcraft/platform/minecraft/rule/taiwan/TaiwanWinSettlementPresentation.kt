package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementFieldIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.FallbackWinSettlementDetailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationContainerStyle
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplate

/** 台灣麻將特殊花牌和牌的結算版面與文字。 */
internal object TaiwanWinSettlementPresentation {
    /** 台灣麻將結算模板 key。 */
    val TEMPLATE_KEY: String = MahjongCraftMetadata.id("taiwan/win_settlement")

    /** 結算標題欄位：特殊花牌和牌顯示花胡名稱，其餘顯示這次和牌動作的名稱。 */
    val OUTCOME_TITLE: PresentationFieldId = PresentationFieldId(MahjongCraftMetadata.id("taiwan/outcome_title"))

    /** 台種與台數欄位。 */
    val TAI: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.TAI_FIELD)

    /** 台灣麻將目前使用的結算模板。 */
    val TEMPLATE: WinSettlementPresentationTemplate = WinSettlementPresentationTemplate(
        key = TEMPLATE_KEY,
        root = PresentationLayout.Column(
            children = listOf(
                PresentationLayout.Text(OUTCOME_TITLE),
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.WINNER_SUMMARY),
                PresentationLayout.Row(
                    children = listOf(
                        PresentationLayout.TileList(BuiltInWinSettlementFieldIds.COMPLETE_HAND),
                        PresentationLayout.Spacer(width = 5f),
                        PresentationLayout.Tile(BuiltInWinSettlementFieldIds.WINNING_TILE),
                    ),
                    spacing = 2f,
                ),
                PresentationLayout.RepeatEntries(TAI, entriesPerColumn = 4),
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.PAYMENT_SUMMARY),
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE),
            ),
            spacing = 4f,
            style = PresentationContainerStyle(backgroundArgb = 0xCC101722.toInt(), padding = 8f),
        ),
    )

    /** 特殊結果的翻譯鍵；不是台麻特殊花胡時為 null。 */
    fun outcomeTranslationKey(outcomeId: String): String? = when (outcomeId) {
        TaiwanRoundOutcomeIds.EIGHT_FLOWERS -> EIGHT_FLOWERS
        TaiwanRoundOutcomeIds.SEVEN_FLOWERS -> SEVEN_FLOWERS
        else -> null
    }

    /** 將台種 ID 與台數轉成玩家可讀文字。 */
    val taiText: WinSettlementDetailTextFormatter = object : WinSettlementDetailTextFormatter {
        override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = FallbackWinSettlementDetailTextFormatter.quantities(quantities)

        override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = PresentationValue.EntryListValue(
            entries.map { entry ->
                PresentationValue.EntryListValue.Entry(
                    translationKey = taiTypeTranslationKey(entry.id) ?: entry.id,
                    trailingTranslationKey = TAI_COUNT,
                    trailingTranslationArgument = entry.quantity?.amount?.toString(),
                    highlighted = true,
                )
            },
        )
    }

    /** 台種 ID 的翻譯鍵；目前只處理特殊花牌和牌。 */
    private fun taiTypeTranslationKey(id: String): String? = when (id) {
        MahjongCraftMetadata.id("taiwan/tai/eight_flowers") -> EIGHT_FLOWERS
        MahjongCraftMetadata.id("taiwan/tai/seven_flowers") -> SEVEN_FLOWERS
        else -> null
    }

    const val EIGHT_FLOWERS: String = "mahjongcraft.taiwan.outcome.eight_flowers"
    const val SEVEN_FLOWERS: String = "mahjongcraft.taiwan.outcome.seven_flowers"
    const val TAI_COUNT: String = "mahjongcraft.taiwan.settlement.tai_count"
}
