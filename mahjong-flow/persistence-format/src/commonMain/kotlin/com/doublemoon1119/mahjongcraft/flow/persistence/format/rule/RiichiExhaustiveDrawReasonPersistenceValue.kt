package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import kotlinx.serialization.Serializable

/** [RiichiExhaustiveDrawReason] 種類的 persistence 值。 */
@Serializable
enum class RiichiExhaustiveDrawReasonPersistenceValue {
    /** 牌牆耗盡且無人和牌的一般流局。 */
    NORMAL,

    /** 首巡摸牌後，手牌有九種以上不同的么九牌或字牌。 */
    KYUUSHU_KYUUHAI,

    /** 首巡中四名玩家都打出相同的風牌。 */
    SUUFON_RENDA,

    /** 全場共進行四次槓牌，且不是同一玩家完成四次。 */
    SUUKAN_NAGARE,

    /** 四名玩家都已宣告立直。 */
    SUUCHA_RIICHI,

    /** 同一張牌有三家（或政策設為途中流局時的兩家）可榮和，因處理設定而流局；捨牌與搶槓共用此原因。 */
    SANCHA_HOU,
}
