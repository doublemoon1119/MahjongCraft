package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** 驗證純手牌操作也拒絕重複牌、錯誤來源與不存在的持有牌。 */
class HandClaimValidationTest {
    /** 從他家鳴取的來源牌不能標成自家方向，避免呈現時沒有可放置來源牌的位置。 */
    @Test
    fun `meld rejects a claimed source with self direction`() {
        val tiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        assertFailsWith<IllegalArgumentException> {
            Meld(
                type = MeldType.PON,
                tiles = tiles,
                sourceTile = tiles.last(),
                sourceDirection = RelativeDirection.Self,
                publiclyRevealedTileIds = tiles.map { it.id }.toSet(),
                faceDownTileIds = emptySet(),
            )
        }
    }

    /** 鳴牌不能加入本人未持有的牌，也不能重複消耗同一張牌。 */
    @Test
    fun `call rejects foreign and duplicate hand tiles`() {
        val owned = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val foreign = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val source = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val hand = Hand(tiles = listOf(owned))

        assertFailsWith<IllegalArgumentException> {
            hand.call(MeldType.PON, listOf(owned, foreign, source), source, RelativeDirection.Left, setOf(owned.id, foreign.id, source.id), emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            hand.call(MeldType.PON, listOf(owned, owned, source), source, RelativeDirection.Left, setOf(owned.id, source.id), emptySet())
        }
    }

    /** 來源必須是獨立的他家牌，並且確實列在新副露中。 */
    @Test
    fun `call rejects missing or self owned source tile`() {
        val tiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.Green) }
        val foreignSource = FakeIdentifiedTileFactory.create(Tile.Honor.Green)
        val hand = Hand(tiles = tiles)

        assertFailsWith<IllegalArgumentException> {
            hand.call(MeldType.PON, tiles, foreignSource, RelativeDirection.Left, tiles.map { it.id }.toSet(), emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            hand.call(MeldType.PON, tiles, tiles.first(), RelativeDirection.Left, tiles.map { it.id }.toSet(), emptySet())
        }
    }

    /** 加槓只能使用本人持有、且尚未屬於任何副露的實體牌。 */
    @Test
    fun `added kan rejects a tile outside the standing hand`() {
        val tiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.Red) }
        val meld = Meld(
            type = MeldType.PON,
            tiles = tiles,
            sourceTile = tiles.last(),
            sourceDirection = RelativeDirection.Left,
            publiclyRevealedTileIds = tiles.mapTo(mutableSetOf()) { it.id },
            faceDownTileIds = emptySet(),
        )
        val hand = Hand(melds = listOf(meld))

        assertFailsWith<IllegalArgumentException> {
            hand.upgradeToAddedKan(FakeIdentifiedTileFactory.create(Tile.Honor.Red), 0)
        }
        assertFailsWith<IllegalArgumentException> {
            hand.upgradeToAddedKan(tiles.first(), 0)
        }
    }
}
