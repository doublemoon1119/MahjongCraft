package com.doublemoon1119.mahjongcraft.logic.judgment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [StandardMeldSearch] 的公式邊界與記憶化。 */
class StandardMeldSearchTest {
    /** 字牌只能組成刻子與對子，連號的字牌不算順子或搭子。 */
    @Test
    fun `honors form only triplets and pairs`() {
        val search = StandardMeldSearch(targetMelds = 4)

        assertEquals(0, search.maxMelds(counts(27 to 1, 28 to 1, 29 to 1)))
        assertEquals(8, search.meldShanten(counts(27 to 1, 28 to 1, 29 to 1), initialMelds = 0))
        assertEquals(1, search.maxMelds(counts(0 to 1, 1 to 1, 2 to 1)))
    }

    /** 面子數超過目標時以目標計，副露已達目標時照原公式回傳。 */
    @Test
    fun `melds beyond the target are capped`() {
        val search = StandardMeldSearch(targetMelds = 4)
        val fiveTriplets = counts(0 to 3, 9 to 3, 18 to 3, 27 to 3, 28 to 3)

        assertEquals(4, search.maxMelds(fiveTriplets))
        assertEquals(0, search.meldShanten(fiveTriplets, initialMelds = 0))
        assertEquals(-2, search.meldShanten(IntArray(34), initialMelds = 5))
    }

    /** 不同組牌的剩餘牌型共用記憶時互不混淆，例如數牌只剩一張 5 與字牌 1、4、1 張。 */
    @Test
    fun `memoized patterns of different groups stay apart`() {
        val search = StandardMeldSearch(targetMelds = 4)

        assertEquals(0, search.maxMelds(counts(4 to 1)))
        assertEquals(1, search.maxMelds(counts(4 to 1, 27 to 1, 28 to 4, 29 to 1)))
    }

    /** 目標面子數必須能放進位元集合。 */
    @Test
    fun `target melds must be supported`() {
        assertFailsWith<IllegalArgumentException> { StandardMeldSearch(targetMelds = 0) }
        assertFailsWith<IllegalArgumentException> { StandardMeldSearch(targetMelds = 8) }
    }

    private fun counts(vararg entries: Pair<Int, Int>): IntArray = IntArray(34).also { counts ->
        entries.forEach { (index, count) -> counts[index] = count }
    }
}
