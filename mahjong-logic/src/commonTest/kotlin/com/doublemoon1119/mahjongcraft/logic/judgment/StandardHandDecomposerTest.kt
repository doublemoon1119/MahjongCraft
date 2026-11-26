package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 標準型拆牌的面子數、多解與胡牌用途測試。 */
class StandardHandDecomposerTest {
    /** 四面子與五面子共用相同搜尋，牌數不符時拒絕。 */
    @Test
    fun `four and five meld hands use the same decomposition`() {
        val four = numbers(1, 2, 3, 4, 5, 6, 7, 8, 9) + List(3) { Tile.Honor.Red } + Tile.Honor.East
        assertTrue(
            actual = StandardHandDecomposer.decompose(
                handTiles = four,
                winningTile = Tile.Honor.East,
                exposedMeldCount = 0,
                targetMelds = 4,
            ).isNotEmpty(),
        )
        assertTrue(
            actual = StandardHandDecomposer.decompose(
                handTiles = four,
                winningTile = Tile.Honor.East,
                exposedMeldCount = 0,
                targetMelds = 5,
            ).isEmpty(),
        )
        val five = four + List(3) { Tile.Honor.Green }
        assertTrue(
            actual = StandardHandDecomposer.decompose(
                handTiles = five,
                winningTile = Tile.Honor.East,
                exposedMeldCount = 0,
                targetMelds = 5,
            ).any { it.groups.size == 5 },
        )
        assertTrue(
            actual = StandardHandDecomposer.decompose(
                handTiles = five,
                winningTile = Tile.Honor.East,
                exposedMeldCount = 0,
                targetMelds = 4,
            ).isEmpty(),
        )
    }

    /** 不能只保留相同雀頭與胡牌用途的第一種拆法。 */
    @Test
    fun `triplet and sequence partitions with the same pair are both retained`() {
        val hand = numbers(1, 1, 1, 2, 2, 2, 3, 3, 3, 7, 8, 9) + Tile.Honor.East
        val result = StandardHandDecomposer.decompose(
            handTiles = hand,
            winningTile = Tile.Honor.East,
            exposedMeldCount = 0,
            targetMelds = 4,
        )
        assertTrue(result.any { it.groups.count { group -> group is StandardHandGroup.Triplet } == 3 })
        assertTrue(result.any { it.groups.all { group -> group is StandardHandGroup.Sequence } })
        assertEquals(
            expected = result,
            actual = StandardHandDecomposer.decompose(
                handTiles = hand.reversed(),
                winningTile = Tile.Honor.East,
                exposedMeldCount = 0,
                targetMelds = 4,
            ),
        )
    }

    /** 胡牌張可同時解為雀頭或順子的一部分。 */
    @Test
    fun `winning tile use is retained when the pair has the same face`() {
        val hand = numbers(1, 1, 2, 3, 4, 5, 6, 7, 8, 9) + List(3) { Tile.Honor.Red }
        val result = StandardHandDecomposer.decompose(
            handTiles = hand,
            winningTile = numbers(1).single(),
            exposedMeldCount = 0,
            targetMelds = 4,
        )
        assertTrue(result.any { it.winningGroupIndex == null })
        assertTrue(result.any { it.winningGroupIndex != null })
    }

    /** 副露按面子數扣除，未宣告的四張牌不自動拆成槓。 */
    @Test
    fun `exposed groups and invalid tile multiplicities are handled explicitly`() {
        assertTrue(
            actual = StandardHandDecomposer.decompose(
                handTiles = listOf(Tile.Honor.East),
                winningTile = Tile.Honor.East,
                exposedMeldCount = 5,
                targetMelds = 5,
            ).isNotEmpty(),
        )
        assertTrue(
            actual = StandardHandDecomposer.decompose(
                handTiles = numbers(1, 1, 1, 1),
                winningTile = numbers(1).single(),
                exposedMeldCount = 4,
                targetMelds = 5,
            ).isEmpty(),
        )
        assertTrue(
            actual = StandardHandDecomposer.decompose(
                handTiles = numbers(1, 2),
                winningTile = numbers(3).single(),
                exposedMeldCount = 5,
                targetMelds = 5,
            ).isEmpty(),
        )
    }

    /** 將點數建立為萬子，供牌例明確列出張數。 */
    private fun numbers(vararg ranks: Int): List<Tile> = ranks.map { Tile.Numeric(Tile.Suit.Character, it) }
}
