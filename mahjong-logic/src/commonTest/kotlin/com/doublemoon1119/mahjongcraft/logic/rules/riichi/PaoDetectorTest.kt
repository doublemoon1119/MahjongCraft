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
     * 測試碰第三組三元牌時，另外兩組皆為手牌中的暗刻，應成立大三元包牌。
     */
    @Test
    fun `test daisangen pao when calling third dragon with two concealed triplets`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.Red,
                Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green
            )
        )

        val result = PaoDetector.check(hand, Tile.Honor.White, RelativeDirection.Left)

        assertEquals(PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left), result)
    }

    /**
     * 測試碰第三組三元牌時，其中一組已經是先前碰過的副露，另一組為暗刻，應成立大三元包牌。
     */
    @Test
    fun `test daisangen pao when calling third dragon with one exposed and one concealed`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.Red),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Red),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Red)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )

        val result = PaoDetector.check(hand, Tile.Honor.White, RelativeDirection.Right)

        assertEquals(PaoLiability(PaoYaku.Daisangen, RelativeDirection.Right), result)
    }

    /**
     * 測試只有一組三元牌完成時，碰第二組不應成立包牌（尚未湊齊三組）。
     */
    @Test
    fun `test no pao when only one other dragon group is complete`() {
        val hand = FakeHandFactory.create(
            listOf(Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.Red)
        )

        val result = PaoDetector.check(hand, Tile.Honor.White, RelativeDirection.Left)

        assertNull(result, "Should not trigger pao when only one other dragon group is complete")
    }

    /**
     * 測試另一組三元牌只有 2 張（尚未成刻）時，不應成立包牌。
     */
    @Test
    fun `test no pao when other dragon group has fewer than three tiles`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.Red, Tile.Honor.Red,
                Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green
            )
        )

        val result = PaoDetector.check(hand, Tile.Honor.White, RelativeDirection.Left)

        assertNull(result, "Should not trigger pao when another dragon group only has 2 tiles")
    }

    /**
     * 測試碰第四組風牌時，另外三組皆已完成（2 組副露 + 1 組暗刻），應成立大四喜包牌。
     */
    @Test
    fun `test daisuushii pao when calling fourth wind with three other groups complete`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(Tile.Honor.West, Tile.Honor.West, Tile.Honor.West),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.East),
                        FakeIdentifiedTileFactory.create(Tile.Honor.East),
                        FakeIdentifiedTileFactory.create(Tile.Honor.East)
                    ),
                    sourceDirection = RelativeDirection.Left
                ),
                Meld(
                    type = MeldType.OPEN_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.South),
                        FakeIdentifiedTileFactory.create(Tile.Honor.South),
                        FakeIdentifiedTileFactory.create(Tile.Honor.South),
                        FakeIdentifiedTileFactory.create(Tile.Honor.South)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )

        val result = PaoDetector.check(hand, Tile.Honor.North, RelativeDirection.Right)

        assertEquals(PaoLiability(PaoYaku.Daisuushii, RelativeDirection.Right), result)
    }

    /**
     * 測試呼叫的牌不是三元牌或風牌時，一律不成立包牌。
     */
    @Test
    fun `test no pao when called tile is not dragon or wind`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.Red,
                Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green
            )
        )

        val result = PaoDetector.check(
            hand,
            Tile.Numeric(Tile.Suit.Character, 5),
            RelativeDirection.Left
        )

        assertNull(result, "Non-honor tiles should never trigger pao")
    }
}
