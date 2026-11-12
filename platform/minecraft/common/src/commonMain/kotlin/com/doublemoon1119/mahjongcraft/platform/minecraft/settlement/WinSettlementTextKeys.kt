package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 內建胡牌結算面板與通知使用的 translation key 單一來源。 */
object WinSettlementTextKeys {
    /** 所有 MahjongCraft 胡牌結算 translation key 的共用前綴。 */
    private const val PREFIX = MinecraftModMetadata.MOD_ID + ".settlement."

    const val RELATIONSHIP_ARROW = PREFIX + "relationship_arrow"
    const val RON_RELATIONSHIP = PREFIX + "ron_relationship"
    const val SCORE_RANKING = PREFIX + "score_ranking"
    const val TEMPLATE_FALLBACK = PREFIX + "template_fallback"
    const val TOTAL_SCORE = PREFIX + "total_score"

    /** 平台語系資源必須提供的全部內建胡牌結算 key。 */
    val ALL: Set<String> = setOf(
        RELATIONSHIP_ARROW,
        RON_RELATIONSHIP,
        SCORE_RANKING,
        TEMPLATE_FALLBACK,
        TOTAL_SCORE,
    )
}
