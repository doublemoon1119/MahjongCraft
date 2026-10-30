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

/** 向聽計算器測試共用的牌型產生與性質檢查。 */
internal object ShantenTestFixtures {
    /** 34 格計數陣列中各位置的牌：萬、筒、條的 1～9，接著東南西北白發中。 */
    val TILE_KINDS: List<Tile> = Tile.Suit.entries.flatMap { suit -> (1..9).map { Tile.Numeric(suit, it) } } +
        listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North, Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red)

    /** 副露的種類輪替使用，讓槓與碰、吃都出現；向聽計算只看副露的組數。 */
    private val MELD_TYPES = listOf(MeldType.PON, MeldType.CHI, MeldType.CLOSED_KAN, MeldType.OPEN_KAN, MeldType.ADDED_KAN)

    /**
     * 由立牌與 [meldCount] 組副露建立手牌。
     *
     * @param tiles 立牌。
     * @param meldCount 副露組數。
     * @return 含指定立牌與副露的測試手牌。
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
     * 尚未摸牌（例如立直麻將的 13 張）的手牌摸進一張牌時，應符合以下性質：
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

    /**
     * 取得測試比對使用的向聽數：和牌為 -1、聽牌為 0。
     *
     * @param result 計算器回傳的向聽結果。
     * @return 對應的整數向聽數。
     */
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
     * 每種牌各四張的牌山。
     *
     * @param replace 接收每張牌與它是第幾張（0～3），回傳替換後的牌；回傳 null 表示移除，例如拿掉三人麻將不用的牌。
     * @return 依替換函式產生的牌山列表。
     */
    fun fullWall(replace: (Tile, Int) -> Tile? = { tile, _ -> tile }): List<Tile> = TILE_KINDS.flatMap { tile -> (0 until 4).mapNotNull { copy -> replace(tile, copy) } }

    /**
     * 建立失敗訊息中的手牌摘要。
     *
     * @param hand 要描述的手牌。
     * @return 立牌內容與副露組數的文字。
     */
    private fun describe(hand: Hand): String = "standing=${hand.standingTiles.map { it.tile }}, melds=${hand.exposedMelds.size}"

    /**
     * 建立 [count] 組白板副露，供向聽測試使用。
     *
     * @param count 要建立的副露組數。
     * @return 副露清單；每組的型別依測試用清單輪替。
     */
    private fun melds(count: Int): List<Meld> = List(count) { index ->
        val type = MELD_TYPES[index % MELD_TYPES.size]
        val size = if (type == MeldType.PON || type == MeldType.CHI) 3 else 4
        Meld(
            type = type,
            tiles = List(size) { FakeIdentifiedTileFactory.create(Tile.Honor.White) },
            sourceDirection = RelativeDirection.Left,
            publiclyRevealedTileIds = emptySet(),
            faceDownTileIds = emptySet(),
        )
    }
}
