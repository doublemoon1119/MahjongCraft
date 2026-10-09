package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 向聽計算器測試共用的牌型產生，以及新舊實作的結果比對。 */
internal object ShantenTestFixtures {
    /** 34 格計數陣列中各位置的牌：萬、筒、條的 1～9，接著東南西北白發中。 */
    val TILE_KINDS: List<Tile> = Tile.Suit.entries.flatMap { suit -> (1..9).map { Tile.Numeric(suit, it) } } +
        listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North, Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red)

    /** 副露的種類輪替使用，讓槓與碰、吃都出現；向聽計算只看副露的組數。 */
    private val MELD_TYPES = listOf(MeldType.PON, MeldType.CHI, MeldType.CLOSED_KAN, MeldType.OPEN_KAN, MeldType.ADDED_KAN)

    /**
     * 依每格張數列舉 [positions] 格、總張數為 [total] 的所有牌型。
     *
     * @param positions 格數。
     * @param total 總張數。
     * @param maxPerKind 每格最多張數。
     * @param block 接收每個牌型；陣列在呼叫之間重複使用。
     */
    fun forEachPattern(positions: Int, total: Int, maxPerKind: Int = 4, block: (IntArray) -> Unit) {
        val counts = IntArray(positions)
        fun fill(position: Int, remaining: Int) {
            if (position == positions - 1) {
                if (remaining <= maxPerKind) {
                    counts[position] = remaining
                    block(counts)
                }
                return
            }
            for (count in 0..minOf(maxPerKind, remaining)) {
                counts[position] = count
                fill(position + 1, remaining - count)
            }
        }
        fill(0, total)
    }

    /**
     * 由 34 格中的一段計數建立手牌。
     *
     * @param counts 該段每格的張數。
     * @param offset 該段第一格在 34 格中的位置。
     * @param meldCount 副露組數。
     */
    fun handOf(counts: IntArray, offset: Int, meldCount: Int): Hand {
        val tiles = counts.withIndex().flatMap { (index, count) -> List(count) { TILE_KINDS[offset + index] } }
        return FakeHandFactory.create(tiles = tiles, melds = melds(meldCount))
    }

    /**
     * 由立牌與 [meldCount] 組副露建立手牌。
     *
     * @param tiles 立牌。
     * @param meldCount 副露組數。
     */
    fun handOf(tiles: List<Tile>, meldCount: Int): Hand = FakeHandFactory.create(tiles = tiles, melds = melds(meldCount))

    /**
     * 隨機組成 [groups] 組面子（刻子或順子）加一組雀頭的牌，每種牌最多 4 張；拿掉其中一張即為確實聽牌的手牌。
     *
     * @param random 固定 seed 的亂數來源。
     * @param groups 面子組數。
     * @return 打亂順序的 3 × [groups] + 2 張牌。
     */
    fun randomCompleteTiles(random: Random, groups: Int): List<Tile> {
        val counts = IntArray(TILE_KINDS.size)
        fun take(indices: List<Int>): Boolean {
            if (indices.any { index -> counts[index] + indices.count { it == index } > 4 }) return false
            indices.forEach { counts[it]++ }
            return true
        }
        var placed = 0
        while (placed < groups) {
            val group = if (random.nextInt(4) == 0) {
                val index = random.nextInt(TILE_KINDS.size)
                List(3) { index }
            } else {
                val start = random.nextInt(3) * 9 + random.nextInt(7)
                listOf(start, start + 1, start + 2)
            }
            if (take(group)) placed++
        }
        while (true) {
            val index = random.nextInt(TILE_KINDS.size)
            if (take(listOf(index, index))) break
        }
        return counts.withIndex().flatMap { (index, count) -> List(count) { TILE_KINDS[index] } }.shuffled(random)
    }

    /**
     * 聽牌前張數（例如立直的 13 張）的手牌摸進一張牌時，不依賴舊實作的性質：
     *
     * - 向聽數（和牌記為 -1）不會增加，最多減少 1。
     * - 手牌聽牌時，聽牌列表中的牌摸進後和牌，其他牌摸進後不和牌。
     *
     * 只摸手中還不滿 4 張的牌。
     *
     * @param calculator 受測的計算器。
     * @param hand 聽牌前張數、尚未和牌的手牌。
     * @param canonical 把手牌中的牌換成計數用的牌種，例如赤牌換成一般的 5。
     * @param label 失敗訊息中標示情境的文字。
     */
    fun assertDrawProperties(calculator: ShantenCalculator, hand: Hand, canonical: (Tile) -> Tile, label: String) {
        val before = calculator.calculate(hand)
        assertTrue(before != ShantenResult.Complete, "$label: hand before the draw is already complete: ${describe(hand)}")
        val held = hand.standingTiles.groupingBy { canonical(it.tile) }.eachCount()
        TILE_KINDS.filter { (held[it] ?: 0) < 4 }.forEach { tile ->
            val after = calculator.calculate(hand.addTile(FakeIdentifiedTileFactory.create(tile)))
            assertTrue(
                shantenValue(after) in shantenValue(before) - 1..shantenValue(before),
                "$label: drawing $tile changed $before to $after: ${describe(hand)}",
            )
            if (before is ShantenResult.Tenpai) {
                assertEquals(
                    tile in before.winningTiles,
                    after == ShantenResult.Complete,
                    "$label: drawing $tile gave $after for waits ${before.winningTiles}: ${describe(hand)}",
                )
            }
        }
    }

    /** 向聽數：和牌為 -1、聽牌為 0。 */
    private fun shantenValue(result: ShantenResult): Int = when (result) {
        ShantenResult.Complete -> -1
        is ShantenResult.Tenpai -> 0
        is ShantenResult.NotTenpai -> result.shanten
    }

    /**
     * 從 [wall] 隨機抽出 [standing] 張立牌，另加 [meldCount] 組副露。
     *
     * @param random 固定 seed 的亂數來源，讓失敗可以重現。
     * @param wall 可抽的牌，每張只會被抽到一次。
     * @param standing 立牌張數。
     * @param meldCount 副露組數。
     */
    fun randomHand(random: Random, wall: List<Tile>, standing: Int, meldCount: Int): Hand = FakeHandFactory.create(tiles = wall.shuffled(random).take(standing), melds = melds(meldCount))

    /**
     * 從同一花色的 36 張牌（可另加 [extra]）隨機抽出 [standing] 張立牌，用來產生清一色等集中在一個花色的手牌。
     *
     * @param random 固定 seed 的亂數來源。
     * @param suit 花色。
     * @param standing 立牌張數。
     * @param meldCount 副露組數。
     * @param extra 另外加進牌山的牌，例如該花色的赤牌。
     */
    fun randomSingleSuitHand(random: Random, suit: Tile.Suit, standing: Int, meldCount: Int, extra: List<Tile> = emptyList()): Hand {
        val wall = (1..9).flatMap { value -> List(4) { Tile.Numeric(suit, value) } } + extra
        return randomHand(random, wall, standing, meldCount)
    }

    /**
     * 每種牌各四張的牌山。
     *
     * @param replace 接收每張牌與它是第幾張（0～3），回傳替換後的牌；回傳 null 表示移除，例如換成赤牌或拿掉三人麻將不用的牌。
     */
    fun fullWall(replace: (Tile, Int) -> Tile? = { tile, _ -> tile }): List<Tile> = TILE_KINDS.flatMap { tile -> (0 until 4).mapNotNull { copy -> replace(tile, copy) } }

    /**
     * 比對新舊實作的完整結果；不同時印出可重現的手牌。
     *
     * @param legacy 舊實作（對照組）。
     * @param current 新實作。
     * @param hand 要計算的手牌。
     * @param label 失敗訊息中標示情境的文字。
     */
    fun assertSameResult(legacy: ShantenCalculator, current: ShantenCalculator, hand: Hand, label: String) {
        assertEquals(legacy.calculate(hand), current.calculate(hand), "$label: ${describe(hand)}")
    }

    /** 失敗訊息中的手牌內容：立牌與副露組數。 */
    private fun describe(hand: Hand): String = "standing=${hand.standingTiles.map { it.tile }}, melds=${hand.exposedMelds.size}"

    /** [count] 組副露；內容固定為白板，向聽計算只看組數。 */
    private fun melds(count: Int): List<Meld> = List(count) { index ->
        val type = MELD_TYPES[index % MELD_TYPES.size]
        val size = if (type == MeldType.PON || type == MeldType.CHI) 3 else 4
        Meld(
            type = type,
            tiles = List(size) { FakeIdentifiedTileFactory.create(Tile.Honor.White) },
            sourceDirection = RelativeDirection.Left,
        )
    }
}
