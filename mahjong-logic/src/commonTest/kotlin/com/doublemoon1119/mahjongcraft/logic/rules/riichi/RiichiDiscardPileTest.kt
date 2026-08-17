package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 針對 [RiichiDiscardPile] 及其特有邏輯進行單元測試。
 *
 * [RiichiDiscardPile] 為不可變值物件，`discard()`/`takeLast()` 皆回傳新的實例，
 * 因此測試以 `pile = pile.xxx(...)` 的重新賦值方式驗證各項操作。
 */
class RiichiDiscardPileTest {

    /**
     * 驗證日本麻將牌河是否能正確存儲並識別立直捨牌。
     */
    @Test
    fun `test riichi discard entry properties`() {
        var pile = RiichiDiscardPile()
        val tile = FakeIdentifiedTileFactory.create(Tile.Honor.East)

        // 建立並放入立直捨牌紀錄
        val entry = RiichiDiscardEntry(tile, isRiichi = true)
        pile = pile.discard(entry)

        assertEquals(1, pile.entries.size)
        // 驗證泛型是否允許直接存取 RiichiDiscardEntry 特有屬性
        assertTrue(pile.entries.first().isRiichi)
        assertFalse(pile.entries.first().isTaken)
    }

    /**
     * 驗證鳴牌邏輯是否正確作用於最後一張紀錄。
     */
    @Test
    fun `test riichi takeLast behavior`() {
        var pile = RiichiDiscardPile()
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))

        pile = pile.discard(RiichiDiscardEntry(tile1))
        pile = pile.discard(RiichiDiscardEntry(tile2))

        // 模擬第二張牌被鳴走
        pile = pile.takeLast()

        assertTrue(pile.entries[1].isTaken)
        assertFalse(pile.entries[0].isTaken)
    }

    /** 沒有宣告立直時，沒有任何一張牌需要側身標示。 */
    @Test
    fun `sidewaysMarkedTileId returns null when no riichi declared`() {
        var pile = RiichiDiscardPile()
        pile = pile.discard(RiichiDiscardEntry(FakeIdentifiedTileFactory.create(Tile.Honor.East)))

        assertEquals(null, pile.sidewaysMarkedTileId())
    }

    /** 立直宣告牌還沒被鳴走時，側身標示的就是它自己。 */
    @Test
    fun `sidewaysMarkedTileId returns the declared riichi tile id when it is still in the river`() {
        val riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        var pile = RiichiDiscardPile()
        pile = pile.discard(RiichiDiscardEntry(FakeIdentifiedTileFactory.create(Tile.Honor.South)))
        pile = pile.discard(RiichiDiscardEntry(riichiTile, isRiichi = true))

        assertEquals(riichiTile.id, pile.sidewaysMarkedTileId())
    }

    /** 立直宣告牌被鳴走後，側身標記自然移到下一張還留著的牌。 */
    @Test
    fun `sidewaysMarkedTileId shifts to the next not-taken entry after the riichi tile is taken`() {
        val nextTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        var pile = RiichiDiscardPile()
        pile = pile.discard(RiichiDiscardEntry(FakeIdentifiedTileFactory.create(Tile.Honor.East), isRiichi = true))
        pile = pile.takeLast()
        pile = pile.discard(RiichiDiscardEntry(nextTile))

        assertEquals(nextTile.id, pile.sidewaysMarkedTileId())
    }

    /** 立直宣告牌被鳴走、還沒有下一張捨牌時，暫時沒有任何一張需要側身。 */
    @Test
    fun `sidewaysMarkedTileId returns null when the riichi tile is taken and no later discard exists`() {
        var pile = RiichiDiscardPile()
        pile = pile.discard(RiichiDiscardEntry(FakeIdentifiedTileFactory.create(Tile.Honor.East), isRiichi = true))
        pile = pile.takeLast()

        assertEquals(null, pile.sidewaysMarkedTileId())
    }

    /** 立直宣告牌之後連續好幾張捨牌都被鳴走，側身標記仍正確落在第一張還留著的牌上。 */
    @Test
    fun `sidewaysMarkedTileId skips multiple consecutive taken entries after the riichi tile`() {
        val survivingTile = FakeIdentifiedTileFactory.create(Tile.Honor.West)
        var pile = RiichiDiscardPile()
        pile = pile.discard(RiichiDiscardEntry(FakeIdentifiedTileFactory.create(Tile.Honor.East), isRiichi = true))
        pile = pile.takeLast()
        pile = pile.discard(RiichiDiscardEntry(FakeIdentifiedTileFactory.create(Tile.Honor.South)))
        pile = pile.takeLast()
        pile = pile.discard(RiichiDiscardEntry(survivingTile))

        assertEquals(survivingTile.id, pile.sidewaysMarkedTileId())
    }
}
