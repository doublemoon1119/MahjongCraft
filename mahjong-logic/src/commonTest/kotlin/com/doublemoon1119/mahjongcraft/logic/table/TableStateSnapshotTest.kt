package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 針對 [TableStateSnapshot] 與 [TableState.toSnapshot] 進行單元測試。
 *
 * 驗證桌局快照的觀察者可見性計算、牌山揭露邏輯與屬性傳遞。
 */
class TableStateSnapshotTest {

    /**
     * 驗證觀察者能看到自己的手牌，但看不到其他玩家的手牌。
     */
    @Test
    fun `test observer sees own hand but not others`() {
        val observerId = Uuid.random()
        val otherId = Uuid.random()

        val observerTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val otherTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))

        val observer = FakeMahjongPlayerFactory.create(
            id = observerId,
            initialSeat = Wind.EAST,
            hand = Hand(mutableListOf(observerTile))
        )
        val other = FakeMahjongPlayerFactory.create(
            id = otherId,
            initialSeat = Wind.SOUTH,
            hand = Hand(mutableListOf(otherTile))
        )

        val table = FakeTableStateFactory.create(
            players = listOf(observer, other)
        )

        val snapshot = table.toSnapshot(observerId)

        val observerSnapshot = snapshot.players.find { it.id == observerId }!!
        val otherSnapshot = snapshot.players.find { it.id == otherId }!!

        assertEquals(observerTile.tile, observerSnapshot.hand.standingTiles[0].tile)
        assertNull(otherSnapshot.hand.standingTiles[0].tile, "Observer should not see other player's tile info.")
        assertEquals(otherTile.id, otherSnapshot.hand.standingTiles[0].id)
    }

    /**
     * 驗證桌局快照應正確傳遞場風、局數、連莊次數與當前玩家索引。
     */
    @Test
    fun `test snapshot preserves table metadata`() {
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            players = listOf(player),
            prevalentWind = Wind.SOUTH,
            roundNumber = 3,
            comboCount = 2,
            currentPlayerIndex = 0
        )

        val snapshot = table.toSnapshot(player.id)

        assertEquals(Wind.SOUTH, snapshot.prevalentWind)
        assertEquals(3, snapshot.roundNumber)
        assertEquals(2, snapshot.comboCount)
        assertEquals(0, snapshot.currentPlayerIndex)
    }

    /**
     * 驗證桌局快照應保留規則配置引用。
     */
    @Test
    fun `test snapshot retains config reference`() {
        val config = FakeMahjongRuleConfig(
            initialHandSize = 16,
            deadTileCount = 16,
            minimumWinConstraint = 0
        )
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            players = listOf(player),
            config = config
        )

        val snapshot = table.toSnapshot(player.id)

        assertEquals(config, snapshot.config)
        assertEquals(16, snapshot.config.initialHandSize)
        assertEquals(0, snapshot.config.minimumWinConstraint)
    }

    /**
     * 驗證當 dynamicRuleState 為 null 時，快照中的 dynamicRuleState 也應為 null。
     */
    @Test
    fun `test snapshot with no dynamic rule state`() {
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            players = listOf(player),
            dynamicRuleState = null
        )

        val snapshot = table.toSnapshot(player.id)

        assertNull(snapshot.dynamicRuleState)
    }

    /**
     * 驗證桌局快照應保留 dynamicRuleState 的引用。
     */
    @Test
    fun `test snapshot retains dynamicRuleState reference`() {
        val dynamicState = object : DynamicRuleState {}
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            players = listOf(player),
            dynamicRuleState = dynamicState
        )

        val snapshot = table.toSnapshot(player.id)

        assertEquals(dynamicState, snapshot.dynamicRuleState)
    }

    /**
     * 驗證 TableState.toSnapshot 的 ID 應與原始 TableState 一致。
     */
    @Test
    fun `test snapshot retains table id`() {
        val tableId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            id = tableId,
            players = listOf(player)
        )

        val snapshot = table.toSnapshot(player.id)

        assertEquals(tableId, snapshot.id)
    }
}
