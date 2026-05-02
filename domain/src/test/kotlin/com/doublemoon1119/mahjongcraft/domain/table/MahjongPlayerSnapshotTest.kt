package com.doublemoon1119.mahjongcraft.domain.table

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.table.FakeMahjongPlayerFactory
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 針對 [MahjongPlayerSnapshot] 與 [MahjongPlayer.toSnapshot] 進行單元測試。
 *
 * 驗證玩家快照的手牌可見性控制與其他屬性傳遞的正確性。
 */
class MahjongPlayerSnapshotTest {

    /**
     * 驗證當 isVisible 為 true 時，玩家快照應包含完整的手牌資訊。
     */
    @Test
    fun `test toSnapshot with visible hand preserves hand tiles`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        val player = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = mutableListOf(tile1, tile2)
            )
        )

        val snapshot = player.toSnapshot(isVisible = true)

        assertEquals(player.id, snapshot.id)
        assertEquals(Wind.EAST, snapshot.initialSeat)
        assertEquals(2, snapshot.hand.standingTiles.size)
        assertEquals(tile1.tile, snapshot.hand.standingTiles[0].tile)
        assertEquals(tile2.tile, snapshot.hand.standingTiles[1].tile)
    }

    /**
     * 驗證當 isVisible 為 false 時，玩家快照的手牌資訊應被隱藏，僅保留 ID。
     */
    @Test
    fun `test toSnapshot with hidden hand hides tile info but keeps ids`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        val player = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(
                tiles = mutableListOf(tile1, tile2)
            )
        )

        val snapshot = player.toSnapshot(isVisible = false)

        assertEquals(player.id, snapshot.id)
        assertEquals(Wind.SOUTH, snapshot.initialSeat)
        assertEquals(2, snapshot.hand.standingTiles.size)
        assertEquals(tile1.id, snapshot.hand.standingTiles[0].id)
        assertNull(snapshot.hand.standingTiles[0].tile)
        assertEquals(tile2.id, snapshot.hand.standingTiles[1].id)
        assertNull(snapshot.hand.standingTiles[1].tile)
    }

    /**
     * 驗證玩家快照應正確傳遞牌河、規則狀態與分數。
     */
    @Test
    fun `test toSnapshot preserves discardPile playerRuleState and score`() {
        val id = UUID.randomUUID()
        val discardPile = FakeDiscardPile()
        val player = FakeMahjongPlayerFactory.create(
            id = id,
            initialSeat = Wind.WEST,
            discardPile = discardPile
        )
        player.score = 25000

        val snapshot = player.toSnapshot(isVisible = true)

        assertEquals(id, snapshot.id)
        assertEquals(Wind.WEST, snapshot.initialSeat)
        assertEquals(discardPile, snapshot.discardPile)
        assertNull(snapshot.playerRuleState)
        assertEquals(25000, snapshot.score)
    }
}
