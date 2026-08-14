package com.doublemoon1119.mahjongcraft.logic.rules.riichi.opening

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpeningPolicy

/**
 * 四人日本麻將的雙骰牌牆開門規則。
 *
 * 依 WRC 規則，以莊家為一開始逆時針數骰子總點數決定牌牆面；再從該面玩家視角的右端數相同墩數
 * 建立缺口。四人日麻的雙骰總和為 `2..12`，不會超過每面 17 墩。
 */
object RiichiWallOpeningPolicy : WallOpeningPolicy {
    /** 四人日本麻將使用兩顆六面骰。 */
    override val diceCount: Int = 2

    /** 依雙骰總點數解析牌牆面與從右側計算的墩數。 */
    override fun resolve(diceRoll: DiceRollResult): WallOpening {
        require(diceRoll.values.size == diceCount) {
            "Riichi wall opening requires exactly $diceCount dice"
        }
        return WallOpening(
            wallSideOffsetFromDealer = (diceRoll.total - 1) % PLAYER_COUNT,
            stacksFromRight = diceRoll.total,
        )
    }

    /** 四人日本麻將的玩家及牌牆面數量。 */
    private const val PLAYER_COUNT: Int = 4
}
