package com.doublemoon1119.mahjongcraft.logic.table.opening

/** 由權威初始化流程提供每次擲骰結果的來源。 */
fun interface DiceRollSource {
    /**
     * 產生指定數量的六面骰結果。
     *
     * @param diceCount 這次需要的骰子顆數。
     * @return 每顆骰子的點數，顆數必須與要求相同。
     */
    fun roll(diceCount: Int): DiceRollResult
}
