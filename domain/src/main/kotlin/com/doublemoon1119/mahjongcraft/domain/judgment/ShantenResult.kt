package com.doublemoon1119.mahjongcraft.domain.judgment

import com.doublemoon1119.mahjongcraft.domain.base.Tile

/**
 * 表示向聽數計算結果的 sealed class。
 *
 * 用清晰的狀態機表達手牌的三種狀態：已胡牌、聽牌、n向聽。
 * 每種狀態攜帶不同的資訊供上層邏輯使用。
 *
 * @see ShantenCalculator 向聽數計算器
 */
sealed class ShantenResult {

    /**
     * 手牌已完成（可胡牌）。
     *
     * @property han 番數。若為 null 表示尚未計算番數。
     *               TODO: 未來可擴展為計算實際番數，用於判斷是否滿足起胡番數限制。
     */
    data class Complete(
        val han: Int? = null
    ) : ShantenResult()

    /**
     * 手牌已聽牌（聽牌中）。
     *
     * @property winningTiles 玩家可以胡的牌列表（即「聽牌」列表）。
     */
    data class Tenpai(
        val winningTiles: List<Tile>
    ) : ShantenResult()

    /**
     * 手牌尚未聽牌。
     *
     * @property shanten 距離聽牌還差幾張牌（即「向聽數」）。
     *                  例如：1 表示還差 1 張牌即可聽牌。
     */
    data class NotTenpai(
        val shanten: Int
    ) : ShantenResult()
}
