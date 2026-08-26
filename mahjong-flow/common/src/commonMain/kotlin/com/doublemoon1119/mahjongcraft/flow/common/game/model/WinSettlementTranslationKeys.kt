package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 內建胡牌結算呈現協定使用的 translation key 單一來源。 */
object WinSettlementTranslationKeys {
    /** 所有 MahjongCraft 胡牌結算 translation key 的共用前綴。 */
    private const val PREFIX = MahjongCraftMetadata.PROJECT_ID + ".settlement."

    const val DORA = PREFIX + "dora"
    const val HAN = PREFIX + "han"
    const val HAN_FU = PREFIX + "han_fu"
    const val NAGASHI_MANGAN = PREFIX + "nagashi_mangan"
    const val RELATIONSHIP_ARROW = PREFIX + "relationship_arrow"
    const val RON = PREFIX + "ron"
    const val RON_RELATIONSHIP = PREFIX + "ron_relationship"
    const val RON_SUMMARY = PREFIX + "ron_summary"
    const val SCORE_RANKING = PREFIX + "score_ranking"
    const val TEMPLATE_FALLBACK = PREFIX + "template_fallback"
    const val TOTAL_SCORE = PREFIX + "total_score"
    const val TSUMO = PREFIX + "tsumo"
    const val TSUMO_SUMMARY = PREFIX + "tsumo_summary"
    const val URA_DORA = PREFIX + "ura_dora"

    /** Minecraft 語系資源必須提供的全部內建胡牌結算 key。 */
    val ALL: Set<String> = setOf(
        DORA,
        HAN,
        HAN_FU,
        NAGASHI_MANGAN,
        RELATIONSHIP_ARROW,
        RON,
        RON_RELATIONSHIP,
        RON_SUMMARY,
        SCORE_RANKING,
        TEMPLATE_FALLBACK,
        TOTAL_SCORE,
        TSUMO,
        TSUMO_SUMMARY,
        URA_DORA,
    )
}
