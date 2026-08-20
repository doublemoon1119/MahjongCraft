package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.opening

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.opening.RiichiWallOpeningPolicy
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpeningPolicy

/**
 * 四人台灣麻將的三骰牌牆開門規則。
 *
 * 依莊家為一逆時針數三骰總點數（`3..18`）決定牌牆面；再從該面玩家視角的右端數相同墩數建立缺口，
 * 缺口右側保留的墩數與再往右跨面延伸的墩數會結合成固定張數的王牌。
 * 與四人日麻的雙骰公式（見 [RiichiWallOpeningPolicy]）形狀相同，
 * 只是骰子數量、點數範圍不同；來源：
 * [華人麻將競技聯盟賽事規則](https://cml88.com/%E8%B3%BD%E4%BA%8B%E8%A6%8F%E5%89%87/)。
 */
object TaiwanWallOpeningPolicy : WallOpeningPolicy {
    /** 四人台灣麻將使用三顆六面骰。 */
    override val diceCount: Int = 3

    /** 依三骰總點數解析牌牆面與從右側計算的墩數。 */
    override fun resolve(diceRoll: DiceRollResult): WallOpening {
        require(diceRoll.values.size == diceCount) {
            "Taiwan wall opening requires exactly $diceCount dice"
        }
        return WallOpening(
            wallSideOffsetFromDealer = (diceRoll.total - 1) % PLAYER_COUNT,
            stacksFromRight = diceRoll.total,
        )
    }

    /** 四人台灣麻將的玩家及牌牆面數量。 */
    private const val PLAYER_COUNT: Int = 4
}
