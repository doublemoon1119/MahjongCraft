package com.doublemoon1119.mahjongcraft.flow.api.event

/** 本局完成原因的規則中立分類。 */
enum class RoundCompletionKind {
    /** 有玩家和牌。 */
    WIN,

    /** 牌山耗盡而流局。 */
    EXHAUSTIVE_DRAW,

    /** 九種九牌、四家立直等途中流局。 */
    ABORTIVE_DRAW,

    /** 規則自訂的其他本局完成結果。 */
    SPECIAL,
}
