package com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpeningPolicy

/**
 * 三人日本麻將的雙骰牌牆開門規則。
 *
 * 從莊家起逆時針在三面牌牆之間數骰子總點數決定牌牆面，再從該面玩家視角的右端數相同墩數建立缺口；
 * 雙骰總和 `2..12` 不會超過每面 18 墩。
 */
object ThreePlayerRiichiWallOpeningPolicy : WallOpeningPolicy {
    /** 使用兩顆六面骰。 */
    override val diceCount: Int = 2

    /** 依雙骰總點數解析牌牆面與從右側計算的墩數。 */
    override fun resolve(diceRoll: DiceRollResult): WallOpening {
        require(diceRoll.values.size == diceCount) {
            "Three-player riichi wall opening requires exactly $diceCount dice"
        }
        return WallOpening(
            wallSideOffsetFromDealer = (diceRoll.total - 1) % ThreePlayerRiichiWallLayout.SIDE_COUNT,
            stacksFromRight = diceRoll.total,
        )
    }
}
