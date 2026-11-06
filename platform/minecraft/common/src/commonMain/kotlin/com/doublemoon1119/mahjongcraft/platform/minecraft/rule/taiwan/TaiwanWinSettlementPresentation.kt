package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementFieldIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.FallbackWinSettlementDetailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.StandardWinSettlementLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplate

/** 台灣麻將的胡牌結算版面與文字。 */
internal object TaiwanWinSettlementPresentation {
    /** 台灣麻將結算模板 key。 */
    val TEMPLATE_KEY: String = MahjongCraftMetadata.id("taiwan/win_settlement")

    /** 台種與台數欄位。 */
    val TAI: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.TAI_FIELD)

    /** 合計台數欄位。 */
    val TAI_TOTAL: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.TAI_TOTAL_FIELD)

    /** 贏家公開的花牌欄位；特殊花牌和牌不含讓和牌成立的那張。 */
    val FLOWERS: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.FLOWERS_FIELD)

    /** 讓特殊花牌和牌成立的那張花牌欄位。 */
    val WINNING_FLOWER: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.WINNING_FLOWER_FIELD)

    /** 花牌列的小標題欄位。 */
    val FLOWERS_LABEL: PresentationFieldId = PresentationFieldId(MahjongCraftMetadata.id("taiwan/flowers_label"))

    /** 直接顯示規則提供內容的欄位。 */
    val EXTENSION_FIELDS: List<PresentationFieldId> = listOf(TAI, TAI_TOTAL, FLOWERS, WINNING_FLOWER)

    /**
     * 台灣麻將結算模板：沿用共用骨架，規則專屬列是花牌，清單是台種，左下是合計台數。
     *
     * 花牌列在有花牌時顯示，讓特殊花牌和牌成立的那張隔開接在最後；不攤牌時沒有手牌列，花牌列往上補位。
     */
    val TEMPLATE: WinSettlementPresentationTemplate = WinSettlementPresentationTemplate(
        key = TEMPLATE_KEY,
        root = StandardWinSettlementLayout.root(
            title = BuiltInWinSettlementFieldIds.OUTCOME_TITLE,
            ruleRow = PresentationLayout.IfPresent(
                fieldId = FLOWERS,
                child = StandardWinSettlementLayout.labeledTiles(
                    label = FLOWERS_LABEL,
                    tiles = PresentationLayout.Row(
                        children = listOf(
                            StandardWinSettlementLayout.smallTiles(FLOWERS),
                            PresentationLayout.IfPresent(
                                fieldId = WINNING_FLOWER,
                                child = PresentationLayout.Row(
                                    children = listOf(
                                        PresentationLayout.Spacer(width = 3f),
                                        StandardWinSettlementLayout.smallTiles(WINNING_FLOWER),
                                    ),
                                ),
                            ),
                        ),
                        spacing = 2f,
                    ),
                ),
            ),
            entries = TAI,
            summaries = listOf(StandardWinSettlementLayout.summary(TAI_TOTAL)),
        ),
    )

    /** 將台種 ID 與台數轉成玩家可讀文字；[HIGHLIGHT_TAI] 台以上的台種以強調樣式顯示。 */
    val taiText: WinSettlementDetailTextFormatter = object : WinSettlementDetailTextFormatter {
        override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = FallbackWinSettlementDetailTextFormatter.quantities(quantities)

        override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = PresentationValue.EntryListValue(
            entries.map { entry ->
                PresentationValue.EntryListValue.Entry(
                    translationKey = taiTypeTranslationKey(entry.id) ?: entry.id,
                    trailingTranslationKey = TAI_COUNT,
                    trailingTranslationArgument = entry.quantity?.amount?.toString(),
                    highlighted = entry.quantity?.let { it.unitId == TaiwanWinSettlementIds.TAI_UNIT && it.amount >= HIGHLIGHT_TAI } == true,
                )
            },
        )
    }

    /** 合計台數：「N 台」。 */
    val taiTotalText: WinSettlementDetailTextFormatter = object : WinSettlementDetailTextFormatter {
        override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = quantities.singleOrNull()
            ?.takeIf { it.unitId == TaiwanWinSettlementIds.TAI_UNIT }
            ?.let { PresentationValue.TextValue(TAI_COUNT, listOf(it.amount.toString())) }
            ?: FallbackWinSettlementDetailTextFormatter.quantities(quantities)

        override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = FallbackWinSettlementDetailTextFormatter.entries(entries)
    }

    /** 台種 ID 的翻譯鍵；不是內建台種時為 null，直接顯示 ID。 */
    private fun taiTypeTranslationKey(id: String): String? = TaiwanTaiType.entries
        .firstOrNull { it.id == id }
        ?.let { TAI_KEY_PREFIX + it.id.substringAfter(TAI_ID_PREFIX) }

    /** 台種翻譯鍵的前綴。 */
    private const val TAI_KEY_PREFIX = "mahjongcraft.taiwan.tai."

    /** 台種 ID 中台種路徑之前的部分。 */
    private val TAI_ID_PREFIX = MahjongCraftMetadata.id("taiwan/tai/")

    /** 以強調樣式顯示的最低台數。 */
    const val HIGHLIGHT_TAI: Int = 8

    const val EIGHT_FLOWERS: String = "mahjongcraft.taiwan.outcome.eight_flowers"
    const val SEVEN_FLOWERS: String = "mahjongcraft.taiwan.outcome.seven_flowers"
    const val TAI_COUNT: String = "mahjongcraft.taiwan.settlement.tai_count"

    /** 花牌列小標題的翻譯鍵。 */
    const val FLOWERS_LABEL_KEY: String = "mahjongcraft.taiwan.settlement.flowers"
}
