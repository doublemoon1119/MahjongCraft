package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 針對 [isFirstGoAround] 進行單元測試。
 *
 * 驗證「場上沒有任何人鳴牌、且每個人都還沒打出第二張牌」這個第一巡判斷邏輯。
 */
class RiichiFirstGoAroundTest {

    /**
     * 驗證開局後、所有人都還沒打過牌時，仍在第一巡。
     */
    @Test
    fun `test is first go around when nobody has discarded yet`() {
        val player = FakeMahjongPlayerFactory.create()
        val otherPlayer = FakeMahjongPlayerFactory.create()
        val table = FakeTableStateFactory.create(players = listOf(player, otherPlayer))

        assertTrue(table.isFirstGoAround(player))
    }

    /**
     * 驗證除了自己以外，其他人都最多打過一張牌時，仍在第一巡。
     */
    @Test
    fun `test is first go around when other players have discarded exactly once`() {
        val player = FakeMahjongPlayerFactory.create()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val otherPlayer = FakeMahjongPlayerFactory.create(
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        val table = FakeTableStateFactory.create(players = listOf(player, otherPlayer))

        assertTrue(table.isFirstGoAround(player))
    }

    /**
     * 驗證自己已經打過牌時，不再是第一巡。
     */
    @Test
    fun `test is not first go around when player has already discarded`() {
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create(
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        val table = FakeTableStateFactory.create(players = listOf(player))

        assertFalse(table.isFirstGoAround(player))
    }

    /**
     * 驗證有人打出過第二張牌時，不再是第一巡。
     */
    @Test
    fun `test is not first go around when someone discarded twice`() {
        val player = FakeMahjongPlayerFactory.create()
        var otherDiscardPile = FakeDiscardPile()
        otherDiscardPile = otherDiscardPile.discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))
        otherDiscardPile = otherDiscardPile.discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South))
        val otherPlayer = FakeMahjongPlayerFactory.create(discardPile = otherDiscardPile)
        val table = FakeTableStateFactory.create(players = listOf(player, otherPlayer))

        assertFalse(table.isFirstGoAround(player))
    }

    /**
     * 驗證場上已有人鳴牌時，不再是第一巡。
     */
    @Test
    fun `test is not first go around when someone has an exposed meld`() {
        val player = FakeMahjongPlayerFactory.create()
        val meldTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val otherPlayer = FakeMahjongPlayerFactory.create(
            hand = Hand().call(
                type = MeldType.PON,
                tiles = List(3) { meldTile },
                source = meldTile,
                direction = RelativeDirection.Left
            )
        )
        val table = FakeTableStateFactory.create(players = listOf(player, otherPlayer))

        assertFalse(table.isFirstGoAround(player))
    }
}
