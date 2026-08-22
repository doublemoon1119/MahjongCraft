package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState

/**
 * 日本麻將特有的玩家狀態。
 *
 * 用於記錄玩家在遊戲過程中的立直相關狀態。
 *
 * @property riichiTile 玩家立直時打出的牌，若未立直則為 null。
 * @property doubleRiichiTile 玩家雙立直時打出的牌，若未雙立直則為 null。
 * @property isIppatsu 玩家立直時為 true，摸下一張牌之後或者期間有其他人鳴牌就會設為 false
 * @property paoLiability 本局是否已成立包牌責任（[PaoDetector] 判定後寫入），若無則為 null。
 *                        一旦成立即持續有效直到本局結束，胡牌結算時供 [RiichiHandValueContextCalculator] 讀取。
 * @property isPermanentlyFuriten 立直後是否已經放棄過一次和牌機會（他家打出和牌張時放過、或自己摸到
 *                        和牌張卻選擇打出）——一旦成立就持續有效直到本局結束，不會隨 `passedTilesInRound`
 *                        清空而恢復，這位玩家之後整局都不能榮和，只能自摸。由
 *                        [MahjongRuleModule.onPlayerDeclinedWin] 設定為 `true`，本局結束由
 *                        [RiichiRuleModule.createInitialPlayerRuleState] 自然重置。
 */
data class RiichiPlayerState(
    val riichiTile: IdentifiedTile? = null,
    val doubleRiichiTile: IdentifiedTile? = null,
    val isIppatsu: Boolean = false,
    val paoLiability: PaoLiability? = null,
    val isPermanentlyFuriten: Boolean = false,
) : PlayerRuleState {

    /**
     * 玩家是否已宣告 立直/雙立直。
     */
    val isRiichi: Boolean get() = riichiTile != null || doubleRiichiTile != null

    /**
     * 玩家是否已宣告雙立直。
     */
    val isDoubleRiichi: Boolean get() = doubleRiichiTile != null

    /**
     * 取得玩家當前振聽的牌列表。
     *
     * 振聽牌來源：
     * 1. 自己打過的牌（discardPile，包含被鳴走的牌）
     * 2. 當前巡迴放過的榮和（passedTilesInRound）
     *
     * @param discardPile 玩家的牌河。
     * @param passedTilesInRound 當前巡迴中放過的牌（用於同巡振聽）。
     * @return 玩家當前振聽的牌集合。
     */
    fun getFuritenTiles(
        discardPile: DiscardPile<*>,
        passedTilesInRound: Set<Tile>,
    ): Set<Tile> {
        val discardedTiles = discardPile.entries
            .map { it.tile.tile.riichiCanonical }
            .toSet()

        return discardedTiles + passedTilesInRound.map { it.riichiCanonical }
    }
}
