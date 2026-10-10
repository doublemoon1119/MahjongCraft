package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 中途胡牌（本局在胡牌後繼續）時，這次結算要提供給玩家的資訊範圍。
 *
 * 只影響結算內容；「這位玩家胡了」本身一律要讓所有玩家知道，不受此設定影響。
 */
enum class ContinuingWinSettlementDetail {
    /** 提供贏家的完整詳情（例如役種、翻符、手牌）與分數變動。 */
    WINNER_DETAILS,

    /** 只提供分數變動；贏家的牌已經公開在桌上，詳情不再另外提供。 */
    SCORE_CHANGES_ONLY,
}
