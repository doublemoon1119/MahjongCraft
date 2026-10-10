package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 規則中立的內建胡牌結算模板。 */
object BuiltInWinSettlementTemplates {
    private val BACKGROUND = PresentationContainerStyle(backgroundArgb = 0xCC101722.toInt(), padding = 8f)

    /** 通用 fallback 模板；不包含寶牌、翻數、符數等規則專屬概念。 */
    val GENERIC: WinSettlementPresentationTemplate = WinSettlementPresentationTemplate(
        key = BuiltInWinSettlementTemplateKeys.GENERIC,
        root = PresentationLayout.Column(
            children = listOf(
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.OUTCOME_TITLE),
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.WINNER_SUMMARY),
                PresentationLayout.Row(
                    children = listOf(
                        PresentationLayout.TileList(BuiltInWinSettlementFieldIds.COMPLETE_HAND),
                        PresentationLayout.Spacer(width = 5f),
                        PresentationLayout.Tile(BuiltInWinSettlementFieldIds.WINNING_TILE),
                    ),
                    spacing = 2f,
                ),
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.PAYMENT_SUMMARY),
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE),
            ),
            spacing = 4f,
            style = BACKGROUND,
        ),
    )
}
