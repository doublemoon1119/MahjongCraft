package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [PaoDetector] 的單元測試類別。
 */
class PaoDetectorTest {

    /**
     * 測試另外兩組三元牌都已碰出時，碰第三組（第 3 組副露）應成立大三元包牌。
     */
    @Test
    fun `test daisangen pao when calling the third dragon meld`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(Tile.Honor.White, Tile.Honor.White),
            melds = listOf(meld(MeldType.PON, Tile.Honor.Red), meld(MeldType.PON, Tile.Honor.Green)),
        )

        val result = PaoDetector.check(hand, Tile.Honor.White, RelativeDirection.Right)

        assertEquals(PaoLiability(PaoYaku.Daisangen, RelativeDirection.Right), result)
    }

    /**
     * 測試另外兩組三元牌都是手中暗刻時，碰第三組只是第 1 組副露，不應成立包牌。
     */
    @Test
    fun `test no pao when the other dragon groups are concealed triplets`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Honor.Green,
            ),
        )

        val result = PaoDetector.check(hand, Tile.Honor.White, RelativeDirection.Left)

        assertNull(result, "Concealed triplets should not count toward pao")
    }

    /**
     * 測試另外兩組三元牌一組碰出、一組暗刻時，碰第三組只是第 2 組副露，不應成立包牌。
     */
    @Test
    fun `test no pao when one other dragon group is still concealed`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green),
            melds = listOf(meld(MeldType.PON, Tile.Honor.Red)),
        )

        val result = PaoDetector.check(hand, Tile.Honor.White, RelativeDirection.Right)

        assertNull(result, "A concealed triplet should not count toward pao")
    }

    /**
     * 測試暗槓也算副露：一組碰出、一組暗槓時，碰第三組應成立大三元包牌。
     */
    @Test
    fun `test daisangen pao counts a closed kan as a meld`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(Tile.Honor.White, Tile.Honor.White),
            melds = listOf(meld(MeldType.PON, Tile.Honor.Red), meld(MeldType.CLOSED_KAN, Tile.Honor.Green)),
        )

        val result = PaoDetector.check(hand, Tile.Honor.White, RelativeDirection.Across)

        assertEquals(PaoLiability(PaoYaku.Daisangen, RelativeDirection.Across), result)
    }

    /**
     * 測試只有一組三元牌碰出時，碰第二組不應成立包牌。
     */
    @Test
    fun `test no pao when only one other dragon group is exposed`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(Tile.Honor.White, Tile.Honor.White),
            melds = listOf(meld(MeldType.PON, Tile.Honor.Red)),
        )

        val result = PaoDetector.check(hand, Tile.Honor.White, RelativeDirection.Left)

        assertNull(result, "Should not trigger pao when only one other dragon group is exposed")
    }

    /**
     * 測試另外三組風牌都已副露（碰、明槓、暗槓）時，碰第四組應成立大四喜包牌。
     */
    @Test
    fun `test daisuushii pao when calling the fourth wind meld`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(Tile.Honor.North, Tile.Honor.North),
            melds = listOf(
                meld(MeldType.PON, Tile.Honor.East),
                meld(MeldType.OPEN_KAN, Tile.Honor.South),
                meld(MeldType.CLOSED_KAN, Tile.Honor.West),
            ),
        )

        val result = PaoDetector.check(hand, Tile.Honor.North, RelativeDirection.Right)

        assertEquals(PaoLiability(PaoYaku.Daisuushii, RelativeDirection.Right), result)
    }

    /**
     * 測試另外三組風牌中仍有一組是手中暗刻時，碰第四組不應成立大四喜包牌。
     */
    @Test
    fun `test no pao when one other wind group is still concealed`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(Tile.Honor.West, Tile.Honor.West, Tile.Honor.West, Tile.Honor.North, Tile.Honor.North),
            melds = listOf(meld(MeldType.PON, Tile.Honor.East), meld(MeldType.OPEN_KAN, Tile.Honor.South)),
        )

        val result = PaoDetector.check(hand, Tile.Honor.North, RelativeDirection.Right)

        assertNull(result, "A concealed wind triplet should not count toward pao")
    }

    /**
     * 測試呼叫的牌不是三元牌或風牌時，一律不成立包牌。
     */
    @Test
    fun `test no pao when called tile is not dragon or wind`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(Tile.Numeric(Tile.Suit.Character, 5), Tile.Numeric(Tile.Suit.Character, 5)),
            melds = listOf(meld(MeldType.PON, Tile.Honor.Red), meld(MeldType.PON, Tile.Honor.Green)),
        )

        val result = PaoDetector.check(
            hand,
            Tile.Numeric(Tile.Suit.Character, 5),
            RelativeDirection.Left,
        )

        assertNull(result, "Non-honor tiles should never trigger pao")
    }

    /** 建立指定種類、由同一種牌組成的副露；槓為 4 張，其餘為 3 張。 */
    private fun meld(
        type: MeldType,
        tile: Tile,
    ): Meld = Meld(
        type = type,
        tiles = List(if (type == MeldType.OPEN_KAN || type == MeldType.CLOSED_KAN) 4 else 3) { FakeIdentifiedTileFactory.create(tile) },
        sourceDirection = if (type == MeldType.CLOSED_KAN) RelativeDirection.Self else RelativeDirection.Across,
    )
}
