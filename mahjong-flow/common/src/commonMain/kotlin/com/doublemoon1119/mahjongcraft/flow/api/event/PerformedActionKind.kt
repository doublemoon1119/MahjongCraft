package com.doublemoon1119.mahjongcraft.flow.api.event

/** 公開動作事件的中立分類，不綁定任何特定麻將規則。 */
enum class PerformedActionKind {
    /** 打出一張牌。 */
    DISCARD,

    /** 已成立的吃、碰或槓。 */
    MELD,

    /** 宣告一項規則動作。 */
    DECLARATION,

    /** 由公開動作產生的補牌。 */
    SUPPLEMENTAL_DRAW,

    /** 和牌動作。 */
    WIN,

    /** 其他已成立、但沒有更合適分類的動作。 */
    OTHER,
}
