package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementFieldIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.StandardWinSettlementLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplate

/** 台灣麻將的胡牌結算版面：台灣麻將模板與台灣麻將專屬欄位。 */
internal object TaiwanWinSettlementTemplates {
    /** 台灣麻將結算模板 key。 */
    val TEMPLATE_KEY: String = MahjongCraftMetadata.id("taiwan/win_settlement")

    /** 台種與台數欄位。 */
    val TAI: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.TAI_FIELD)

    /** 合計台數欄位。 */
    val TAI_TOTAL: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.TAI_TOTAL_FIELD)

    /** 閒家自摸時莊家那一份的合計台數欄位。 */
    val DEALER_TAI_TOTAL: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.DEALER_TAI_TOTAL_FIELD)

    /** 贏家公開的花牌欄位；特殊花牌和牌不含讓和牌成立的那張。 */
    val FLOWERS: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.FLOWERS_FIELD)

    /** 讓特殊花牌和牌成立的那張花牌欄位。 */
    val WINNING_FLOWER: PresentationFieldId = PresentationFieldId(TaiwanWinSettlementIds.WINNING_FLOWER_FIELD)

    /** 花牌列的小標題欄位。 */
    val FLOWERS_LABEL: PresentationFieldId = PresentationFieldId(MahjongCraftMetadata.id("taiwan/flowers_label"))

    /** 直接顯示規則提供內容的欄位。 */
    val EXTENSION_FIELDS: List<PresentationFieldId> = listOf(TAI, TAI_TOTAL, DEALER_TAI_TOTAL, FLOWERS, WINNING_FLOWER)

    /**
     * 台灣麻將結算模板：沿用共用骨架，規則專屬列是花牌，清單是台種，左下是合計台數；閒家自摸時合計後面接莊家那一份。
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
            summaries = listOf(
                StandardWinSettlementLayout.summary(TAI_TOTAL),
                StandardWinSettlementLayout.summary(DEALER_TAI_TOTAL, argb = StandardWinSettlementLayout.CONDITIONAL_ARGB),
            ),
        ),
    )
}
