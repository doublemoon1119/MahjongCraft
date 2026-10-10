package com.doublemoon1119.mahjongcraft.logic.config

/**
 * 一炮多響（同一張捨牌同時被多位玩家榮和）時，單一情境下的結算方式。
 */
enum class RonResolution {
    /** 頭跳：僅由摸牌順位離放銃者最近的玩家胡牌，其餘玩家視為未榮和。 */
    NEAREST_WINNER,

    /** 多家和：所有符合資格的玩家皆各自胡牌，放銃者需分別支付每一位贏家。 */
    ALL_WINNERS,

    /** 途中流局：直接判定本局流局，所有符合資格的玩家皆不計分。 */
    ABORTIVE_DRAW,
}
