package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.assertSameResult
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.forEachPattern
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.fullWall
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.handOf
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.randomHand
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.randomSingleSuitHand
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import kotlin.random.Random
import kotlin.test.Test

/**
 * [TaiwanShantenCalculator] 與原始窮舉實作 [LegacyTaiwanShantenCalculator] 的完整結果對照。
 *
 * 結果包含是否和牌、向聽數，以及聽牌列表的內容與順序。
 */
class TaiwanShantenCalculatorEquivalenceTest {
    private val legacy = LegacyTaiwanShantenCalculator()
    private val current = TaiwanShantenCalculator()

    /** 副露 0～5 組時，聽牌前（16 張起）與摸牌後（17 張起）的立牌張數。 */
    private val standingSizes = (0..5).flatMap { melds -> listOf(16 - 3 * melds to melds, 17 - 3 * melds to melds) }

    /** 有兩組以上副露時（11 張以下）單一數牌花色的全部牌型。 */
    @Test
    fun `every single suit pattern with melds matches`() {
        standingSizes.filter { (_, melds) -> melds >= 2 }.forEach { (standing, melds) ->
            forEachPattern(positions = 9, total = standing) { counts ->
                assertSameResult(legacy, current, handOf(counts, offset = 0, meldCount = melds), "single suit")
            }
        }
    }

    /** 張數更多的清一色；全部牌型太多，改為固定 seed 抽樣。 */
    @Test
    fun `random large single suit hands match`() {
        val random = Random(SEED)
        val sizes = standingSizes.filter { (_, melds) -> melds < 2 }
        repeat(RANDOM_HANDS) {
            val (standing, melds) = sizes.random(random)
            assertSameResult(legacy, current, randomSingleSuitHand(random, Tile.Suit.Character, standing, melds), "large single suit")
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

    /** 從含花牌的完整牌山隨機抽牌；花牌不參與向聽計算。 */
    @Test
    fun `random hands with flowers match`() {
        val random = Random(SEED + 1)
        val wall = fullWall() + TaiwanTileTypes.createAll()
        repeat(RANDOM_HANDS) {
            val (standing, melds) = standingSizes.random(random)
            assertSameResult(legacy, current, randomHand(random, wall, standing, melds), "with flowers")
        }
    }

    /** 兩個花色加字牌的集中牌型，多種拆法互相競爭、搭子容易超過剩餘面子位置。 */
    @Test
    fun `random dense hands match`() {
        val random = Random(SEED + 2)
        val wall = (1..9).flatMap { value -> List(4) { Tile.Numeric(Tile.Suit.Dot, value) } + List(4) { Tile.Numeric(Tile.Suit.Bamboo, value) } } +
            List(4) { Tile.Honor.Green }
        repeat(RANDOM_HANDS) {
            val (standing, melds) = standingSizes.random(random)
            assertSameResult(legacy, current, randomHand(random, wall, standing, melds), "dense")
        }
    }

    private companion object {
        const val SEED = 20261010
        const val RANDOM_HANDS = 3000
    }
}
