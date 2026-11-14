package com.doublemoon1119.mahjongcraft.testing.logic.table

import com.doublemoon1119.mahjongcraft.logic.table.RoundRandomness
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollSource
import com.doublemoon1119.mahjongcraft.logic.table.opening.RandomDiceRollSource
import kotlin.random.Random

/**
 * 以固定數字建立一局的亂數來源；同一個 [seed] 每次得到相同的座位、牌山、骰子與組件初值，讓測試結果固定。
 *
 * @param seed 決定這一局結果的數字；需要多個不同牌局時傳入不同數字。
 * @param dice 擲骰來源；需要指定點數的測試可傳入固定來源。
 * @return 固定的亂數來源。
 */
fun testRoundRandomness(
    seed: Int = 0,
    dice: DiceRollSource = RandomDiceRollSource(Random(seed + DICE_SEED_OFFSET)),
): RoundRandomness = RoundRandomness(
    seatOrder = Random(seed),
    wall = Random(seed + WALL_SEED_OFFSET),
    dice = dice,
    components = Random(seed + COMPONENT_SEED_OFFSET),
)

/** 牌山亂數與座位亂數的種子差距，讓兩者不是同一串數字。 */
private const val WALL_SEED_OFFSET = 1_000_003

/** 擲骰亂數與座位亂數的種子差距。 */
private const val DICE_SEED_OFFSET = 2_000_003

/** 組件亂數與座位亂數的種子差距。 */
private const val COMPONENT_SEED_OFFSET = 3_000_017
