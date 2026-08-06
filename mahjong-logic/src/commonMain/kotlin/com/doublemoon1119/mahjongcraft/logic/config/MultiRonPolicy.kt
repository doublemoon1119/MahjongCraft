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

/**
 * 一炮多響時，依同時榮和人數決定結算方式的規則配置。
 *
 * 4 人對局扣除放銃者後最多同時有 3 人可榮和，故僅需區分「雙響（恰有 2 人)」與
 * 「三響（3 人以上)」兩種情境，不需要更細緻的門檻設計。
 *
 * @property doubleRonResolution 恰有 2 位玩家同時可榮和同一張牌時的結算方式。
 * @property tripleRonResolution 有 3 位（含以上）玩家同時可榮和同一張牌時的結算方式。
 */
data class MultiRonPolicy(
    val doubleRonResolution: RonResolution,
    val tripleRonResolution: RonResolution,
)
