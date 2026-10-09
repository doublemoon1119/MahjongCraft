package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.assertSameResult
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.forEachPattern
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.fullWall
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.handOf
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.randomHand
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.randomSingleSuitHand
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import kotlin.random.Random
import kotlin.test.Test

/**
 * [RiichiShantenCalculator] 與原始窮舉實作 [LegacyRiichiShantenCalculator] 的完整結果對照。
 *
 * 結果包含是否和牌、向聽數，以及聽牌列表的內容與順序。
 */
class RiichiShantenCalculatorEquivalenceTest {
    private val legacy = LegacyRiichiShantenCalculator()
    private val current = RiichiShantenCalculator()

    /** 副露 0～4 組時，聽牌前（13 張起）與摸牌後（14 張起）的立牌張數。 */
    private val standingSizes = (0..4).flatMap { melds -> listOf(13 - 3 * melds to melds, 14 - 3 * melds to melds) }

    /** 有副露時（11 張以下）單一數牌花色的全部牌型。 */
    @Test
    fun `every single suit pattern with melds matches`() {
        standingSizes.filter { (_, melds) -> melds > 0 }.forEach { (standing, melds) ->
            forEachPattern(positions = 9, total = standing) { counts ->
                assertSameResult(legacy, current, handOf(counts, offset = 0, meldCount = melds), "single suit")
            }
        }
    }

    /** 門前 13、14 張的清一色；全部牌型太多，改為固定 seed 抽樣。 */
    @Test
    fun `random concealed single suit hands match`() {
        val random = Random(SEED + 4)
        repeat(RANDOM_HANDS) {
            val standing = if (random.nextBoolean()) 13 else 14
            assertSameResult(legacy, current, randomSingleSuitHand(random, Tile.Suit.Bamboo, standing, meldCount = 0), "concealed single suit")
        }
    }

    /** 只有字牌的全部牌型；字牌只能組成刻子與對子。 */
    @Test
    fun `every honor pattern matches`() {
        standingSizes.forEach { (standing, melds) ->
            forEachPattern(positions = 7, total = standing) { counts ->
                assertSameResult(legacy, current, handOf(counts, offset = 27, meldCount = melds), "honors")
            }
        }
    }

    /** 從含赤牌的完整牌山隨機抽牌，涵蓋雀頭與搭子分散在不同花色、字牌的情況。 */
    @Test
    fun `random four player hands match`() {
        val random = Random(SEED)
        val wall = fullWall { tile, copy -> if (copy == 0 && tile is Tile.Numeric && tile.value == 5) RiichiTileTypes.redFive(tile.suit) else tile }
        repeat(RANDOM_HANDS) {
            val (standing, melds) = standingSizes.random(random)
            assertSameResult(legacy, current, randomHand(random, wall, standing, melds), "four player")
        }
    }

    /** 三人麻將的牌山（沒有 2～8 萬）。 */
    @Test
    fun `random three player hands match`() {
        val random = Random(SEED + 1)
        val wall = fullWall { tile, _ -> tile.takeUnless { it is Tile.Numeric && it.suit == Tile.Suit.Character && it.value in 2..8 } }
        repeat(RANDOM_HANDS) {
            val (standing, melds) = standingSizes.random(random)
            assertSameResult(legacy, current, randomHand(random, wall, standing, melds), "three player")
        }
    }

    /** 兩個花色加字牌的集中牌型，多種拆法互相競爭、搭子容易超過剩餘面子位置。 */
    @Test
    fun `random dense hands match`() {
        val random = Random(SEED + 2)
        val wall = (1..9).flatMap { value -> List(4) { Tile.Numeric(Tile.Suit.Dot, value) } + List(4) { Tile.Numeric(Tile.Suit.Bamboo, value) } } +
            List(4) { Tile.Honor.Red }
        repeat(RANDOM_HANDS) {
            val (standing, melds) = standingSizes.random(random)
            assertSameResult(legacy, current, randomHand(random, wall, standing, melds), "dense")
        }
    }

    /** 清一色並混入赤牌。 */
    @Test
    fun `random single suit hands with red fives match`() {
        val random = Random(SEED + 3)
        repeat(RANDOM_HANDS) {
            val (standing, melds) = standingSizes.random(random)
            val suit = Tile.Suit.entries.random(random)
            val hand = randomSingleSuitHand(random, suit, standing, melds, extra = listOf(RiichiTileTypes.redFive(suit)))
            assertSameResult(legacy, current, hand, "single suit with red five")
        }
    }

    /** 七對子與國士無雙的聽牌、和牌與一向聽。 */
    @Test
    fun `seven pairs and thirteen orphans match`() {
        val terminals = listOf(
            Tile.Numeric(Tile.Suit.Character, 1), Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Dot, 1), Tile.Numeric(Tile.Suit.Dot, 9),
            Tile.Numeric(Tile.Suit.Bamboo, 1), Tile.Numeric(Tile.Suit.Bamboo, 9),
            Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North, Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red,
        )
        val pairs = listOf(1, 3, 5, 7, 9).map { Tile.Numeric(Tile.Suit.Dot, it) } + listOf(Tile.Honor.East, Tile.Honor.Red)
        val hands = listOf(
            terminals,
            terminals + Tile.Honor.Red,
            terminals.dropLast(1) + Tile.Honor.East,
            pairs.flatMap { listOf(it, it) },
            pairs.flatMap { listOf(it, it) }.dropLast(1),
            pairs.flatMap { listOf(it, it) }.dropLast(2) + Tile.Honor.North,
        )
        hands.forEach { tiles -> assertSameResult(legacy, current, FakeHandFactory.create(tiles), "special hand") }
    }

    private companion object {
        const val SEED = 20261009
        const val RANDOM_HANDS = 3000
    }
}
