package com.doublemoon1119.mahjongcraft.logic.table

/** 本局權威結果的規則中立分類。 */
enum class RoundCompletionClassification {
    /** 胡牌或與胡牌等價的結果。 */
    WIN,

    /** 一般荒牌流局。 */
    EXHAUSTIVE_DRAW,

    /** 途中流局。 */
    ABORTIVE_DRAW,

    /** 第三方規則提供、核心不進一步解讀的結果。 */
    EXTENSION,
}
