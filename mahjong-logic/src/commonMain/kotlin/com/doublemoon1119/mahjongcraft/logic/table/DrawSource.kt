package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/** 一次摸牌的來源。 */
sealed interface DrawSource {
    /** 輪到玩家時從活牌依正常順序摸牌。 */
    data object LiveWall : DrawSource

    /**
     * 動作完成後由 [SupplementalDrawPolicy] 補到的牌，例如槓或移出手牌後的補牌。
     *
     * @property action 觸發這次補牌的動作。
     */
    data class Supplemental(val action: GameAction) : DrawSource
}
