package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeMeldFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證碰或明槓字牌時是否補齊整套字牌副露。 */
class HonorSetCompletionDetectorTest {
    /** 其餘三元牌都已副露（含暗槓）時補齊三元牌。 */
    @Test
    fun `the last dragon completes the dragon set`() {
        val hand = Hand(melds = listOf(meld(MeldType.Pon, Tile.Honor.Red), meld(MeldType.ClosedKan, Tile.Honor.Green)))

        assertEquals(HonorSet.DRAGONS, HonorSetCompletionDetector.completedBy(hand, Tile.Honor.White))
    }

    /** 其餘三種風牌都已副露時補齊風牌。 */
    @Test
    fun `the last wind completes the wind set`() {
        val hand = Hand(melds = listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.North).map { meld(MeldType.Pon, it) })

        assertEquals(HonorSet.WINDS, HonorSetCompletionDetector.completedBy(hand, Tile.Honor.West))
    }

    /** 缺少副露、只有手中暗刻或鳴取數牌時都不補齊。 */
    @Test
    fun `missing melds, concealed triplets and number tiles complete nothing`() {
        val oneMeld = Hand(melds = listOf(meld(MeldType.Pon, Tile.Honor.Red)))
        val concealed = Hand(
            tiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.Green) },
            melds = listOf(meld(MeldType.Pon, Tile.Honor.Red)),
        )

        assertNull(HonorSetCompletionDetector.completedBy(oneMeld, Tile.Honor.White))
        assertNull(HonorSetCompletionDetector.completedBy(concealed, Tile.Honor.White))
        assertNull(HonorSetCompletionDetector.completedBy(oneMeld, Tile.Numeric(Tile.Suit.Dot, 5)))
    }

    /**
     * 建立四張以內相同字牌的副露。
     *
     * @param type 副露種類。
     * @param honor 字牌。
     * @return 副露。
     */
    private fun meld(type: MeldType, honor: Tile.Honor): Meld = FakeMeldFactory.create(
        type = type,
        tiles = List(if (type == MeldType.Pon) 3 else 4) { FakeIdentifiedTileFactory.create(honor) },
    )
}
