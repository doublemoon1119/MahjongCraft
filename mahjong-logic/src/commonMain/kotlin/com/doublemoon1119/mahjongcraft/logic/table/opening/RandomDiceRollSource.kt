package com.doublemoon1119.mahjongcraft.logic.table.opening

/** 使用隨機六面骰的正式來源。 */
object RandomDiceRollSource : DiceRollSource {
    override fun roll(diceCount: Int): DiceRollResult = DiceRollResult.of(List(diceCount) { (1..6).random() })
}
