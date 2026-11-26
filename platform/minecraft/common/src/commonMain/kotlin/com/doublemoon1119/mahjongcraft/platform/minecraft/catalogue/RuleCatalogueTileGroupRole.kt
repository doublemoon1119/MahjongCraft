package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

/** 範例牌組的呈現語意，不表示權威副露狀態。 */
enum class RuleCatalogueTileGroupRole {
    /** 立牌。 */
    HAND,

    /** 獨立和牌張。 */
    WINNING_TILE,

    /** 吃碰等公開副露。 */
    OPEN_MELD,

    /** 明槓或加槓。 */
    OPEN_KAN,

    /** 暗槓。 */
    CLOSED_KAN,

    /** 不代表完整手牌結構的示意牌組。 */
    ILLUSTRATION,
}
