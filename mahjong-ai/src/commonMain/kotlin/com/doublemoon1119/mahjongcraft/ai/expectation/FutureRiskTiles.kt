package com.doublemoon1119.mahjongcraft.ai.expectation

/** 可換牌的手牌估計後續風險時，假設之後打出的牌。 */
enum class FutureRiskTiles {
    /** 每一張都以手中所有牌的平均放銃損失計算。 */
    AVERAGE,

    /** 依序打出手中放銃損失最小的牌；需要打出的張數超過手牌時，超出的部分以平均損失計算。 */
    SAFEST,
}
