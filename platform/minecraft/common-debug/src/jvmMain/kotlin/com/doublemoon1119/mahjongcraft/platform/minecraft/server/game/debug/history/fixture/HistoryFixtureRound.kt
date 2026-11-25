package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 測試牌譜中照腳本進行的一局。
 *
 * 配牌、摸牌與保留牌只寫出需要的部分，其餘位置由同一副牌中剩下的牌依原本的洗牌順序補上。
 *
 * @property dice 開局程序依序擲出的骰子點數。每次擲骰依序取用需要的顆數。
 * @property hands 各家的配牌，以自風區分。
 * @property draws 配牌之後依序從牌山摸到的牌，不分是哪一家摸的。
 * @property reserved 保留牌區中指定位置的牌，位置與規則的保留牌順序相同，例如日麻王牌的嶺上牌與寶牌指示牌。
 * @property steps 依序執行的決定。沒寫到的時候，輪到自己就打出剛摸到的牌，回應一律放過。
 */
data class HistoryFixtureRound(
    val dice: List<Int>,
    val hands: Map<Wind, String>,
    val draws: String = "",
    val reserved: Map<Int, String> = emptyMap(),
    val steps: List<HistoryFixtureStep> = emptyList(),
) {
    init {
        require(dice.all { it in 1..6 }) { "Dice values are 1 to 6" }
        require(reserved.values.all { HistoryFixtureTile.parse(it).size == 1 }) { "Each reserved position holds one tile" }
    }
}
