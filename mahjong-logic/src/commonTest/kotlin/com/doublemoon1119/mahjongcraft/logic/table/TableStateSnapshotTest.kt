package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

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
            hand = Hand(mutableListOf(observerTile)),
        )
        val other = FakeMahjongPlayerFactory.create(
            id = otherId,
            initialSeat = Wind.SOUTH,
            hand = Hand(mutableListOf(otherTile)),
        )

        val table = FakeTableStateFactory.create(
            players = listOf(observer, other),
        )

        val snapshot = table.toSnapshot(setOf(observerId), setAsideTiles = { emptyList() })

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
            currentPlayerIndex = 0,
        )

        val snapshot = table.toSnapshot(setOf(player.id), setAsideTiles = { emptyList() })

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
            minimumWinConstraint = 0,
        )
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            players = listOf(player),
            config = config,
        )

        val snapshot = table.toSnapshot(setOf(player.id), setAsideTiles = { emptyList() })

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
            dynamicRuleState = null,
        )

        val snapshot = table.toSnapshot(setOf(player.id), setAsideTiles = { emptyList() })

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
            dynamicRuleState = dynamicState,
        )

        val snapshot = table.toSnapshot(setOf(player.id), setAsideTiles = { emptyList() })

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
            players = listOf(player),
        )

        val snapshot = table.toSnapshot(setOf(player.id), setAsideTiles = { emptyList() })

        assertEquals(tableId, snapshot.id)
    }

    /**
     * 驗證快照正確傳遞 [TableState.finishedPlayerIds]，供 HUD、牌面與觀戰呈現使用。
     */
    @Test
    fun `test snapshot preserves finishedPlayerIds`() {
        val finishedPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val activePlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            players = listOf(finishedPlayer, activePlayer),
            currentPlayerIndex = 1,
            finishedPlayerIds = setOf(finishedPlayer.id),
        )

        val snapshot = table.toSnapshot(setOf(finishedPlayer.id, activePlayer.id), setAsideTiles = { emptyList() })

        assertEquals(setOf(finishedPlayer.id), snapshot.finishedPlayerIds)
    }

    /** 已公開的牌只要還在手牌中，其他觀察者也看得到；已經離開手牌的不列入，其他手牌照舊隱藏。 */
    @Test
    fun `revealed hand tiles lists revealed tiles still held in hands`() {
        val revealed = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val hidden = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val alreadyPlaced = Uuid.random()
        val declarer = FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(hidden), lastDrawn = revealed))
        val observer = FakeMahjongPlayerFactory.create()
        val table = FakeTableStateFactory.create(players = listOf(declarer, observer))
            .copy(revealedHandTileIds = setOf(revealed.id, alreadyPlaced))

        val snapshot = table.toSnapshot(setOf(observer.id), setAsideTiles = { emptyList() })

        assertEquals(listOf(revealed), snapshot.revealedHandTiles)
        assertNull(snapshot.players.first { it.id == declarer.id }.hand.standingTiles.first { it.id == hidden.id }.tile)
    }

    /** 沒有公開的手牌時不列出任何牌。 */
    @Test
    fun `no revealed hand tiles lists nothing`() {
        val snapshot = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create()))
            .toSnapshot(emptySet(), setAsideTiles = { emptyList() })

        assertEquals(emptyList(), snapshot.revealedHandTiles)
    }
}
