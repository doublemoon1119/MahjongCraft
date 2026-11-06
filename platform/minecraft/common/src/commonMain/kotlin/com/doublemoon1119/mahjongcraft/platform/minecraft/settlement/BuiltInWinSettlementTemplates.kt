package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 規則中立的內建胡牌結算模板。 */
object BuiltInWinSettlementTemplates {
    /** 通用 fallback 模板；沿用 [StandardWinSettlementLayout] 的共用設計，不包含寶牌、翻數、符數等規則專屬概念。 */
    val GENERIC: WinSettlementPresentationTemplate = WinSettlementPresentationTemplate(
        key = BuiltInWinSettlementTemplateKeys.GENERIC,
        root = StandardWinSettlementLayout.root(
            title = BuiltInWinSettlementFieldIds.OUTCOME_TITLE,
            ruleRow = null,
            entries = null,
            summaries = listOf(StandardWinSettlementLayout.summary(BuiltInWinSettlementFieldIds.PAYMENT_SUMMARY)),
        ),
    )
}
