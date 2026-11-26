package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 內建胡牌結算面板與通知使用的 translation key 單一來源。 */
object WinSettlementTextKeys {
    const val RELATIONSHIP_ARROW = "mahjongcraft.settlement.relationship_arrow"
    const val RON_RELATIONSHIP = "mahjongcraft.settlement.ron_relationship"
    const val SCORE_RANKING = "mahjongcraft.settlement.score_ranking"
    const val TEMPLATE_FALLBACK = "mahjongcraft.settlement.template_fallback"
    const val TOTAL_SCORE = "mahjongcraft.settlement.total_score"

    /** 平台語系資源必須提供的全部內建胡牌結算 key。 */
    val ALL: Set<String> = setOf(
        RELATIONSHIP_ARROW,
        RON_RELATIONSHIP,
        SCORE_RANKING,
        TEMPLATE_FALLBACK,
        TOTAL_SCORE,
    )
}
