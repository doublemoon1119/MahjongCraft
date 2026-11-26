package com.doublemoon1119.mahjongcraft.logic.table.opening

import kotlin.random.Random

/**
 * 由指定亂數擲出六面骰的來源。
 *
 * @property random 擲骰使用的亂數來源。
 */
class RandomDiceRollSource(private val random: Random) : DiceRollSource {
    override fun roll(diceCount: Int): DiceRollResult = DiceRollResult.of(List(diceCount) { random.nextInt(from = 1, until = 7) })
}
