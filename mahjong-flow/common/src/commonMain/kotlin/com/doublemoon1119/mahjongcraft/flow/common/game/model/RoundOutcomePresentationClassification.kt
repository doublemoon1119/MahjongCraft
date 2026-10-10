package com.doublemoon1119.mahjongcraft.flow.common.game.model

/** 特殊 round outcome 在後續呈現流程中的規則中立分類。 */
enum class RoundOutcomePresentationClassification {
    /** 外觀與胡牌結算等價，但不代表核心需要理解該規則的計分細節。 */
    WIN_EQUIVALENT,

    /** 外觀與流局結算等價。 */
    EXHAUSTIVE_DRAW_EQUIVALENT,
}
