package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * 針對 [RiichiDynamicState] 與 [TileWallRevealable] 進行單元測試。
 *
 * 驗證寶牌指示器計算、可見牌張判定與槓數關聯邏輯。
 */
class RiichiDynamicStateTest {

    /**
     * 建立一個包含指定數量王牌牌的 TableState，方便測試指示器邏輯。
     */
    private fun createTableStateWithWall(
        deadWallTiles: List<IdentifiedTile>,
        players: List<MahjongPlayer> = emptyList(),
    ): TableState {
        val allTiles = deadWallTiles.toMutableList()
        val tileWall = TileWall(allTiles)
        val config = FakeMahjongRuleConfig(
            deadTileCount = deadWallTiles.size,
        )

        return FakeTableStateFactory.create(
            players = players,
            config = config,
            tileWall = tileWall,
        )
    }

    /**
     * 驗證無槓時，應僅有一張寶牌指示器與一張裏寶牌指示器可見。
     */
    @Test
    fun `test getVisibleTileIds with zero kan shows one dora indicator`() {
        val deadWallTiles = List(14) { i ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (i % 9) + 1))
        }
        val players = listOf(
            FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST),
            FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH),
        )
        val table = createTableStateWithWall(deadWallTiles, players)

        val dynamicState = RiichiDynamicState()
        val visibleIds = dynamicState.getVisibleTileIds(table)

        // 無槓時應有 1 張寶牌指示器可見
        assertEquals(1, visibleIds.size, "Should have exactly one visible dora indicator with zero kan.")
    }

    /**
     * 驗證 getDoraIndicators 在無槓時返回一對指示器。
     */
    @Test
    fun `test getDoraIndicators with zero kan returns one dora and one ura-dora`() {
        val deadWallTiles = List(14) { i ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (i % 9) + 1))
        }
        val table = createTableStateWithWall(deadWallTiles)

        val dynamicState = RiichiDynamicState()
        val (dora, uraDora) = dynamicState.getDoraIndicators(table)

        assertEquals(1, dora.size, "Should have 1 dora indicator with zero kan.")
        assertEquals(1, uraDora.size, "Should have 1 ura-dora indicator with zero kan.")
    }

    /**
     * 驗證每增加一個槓，寶牌指示器數量應增加一張，最多五張。
     */
    @Test
    fun `test getDoraIndicators indicator count increases with kan count`() {
        // 建立測試用玩家，帶有一個明槓
        val kanTiles = List(4) {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        }
        val playerWithKan = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand().call(
                type = MeldType.OPEN_KAN,
                tiles = kanTiles,
                source = kanTiles[0],
                direction = RelativeDirection.Left,
            ),
        )

        val deadWallTiles = List(14) { i ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (i % 9) + 1))
        }
        val table = createTableStateWithWall(deadWallTiles, listOf(playerWithKan))

        val dynamicState = RiichiDynamicState()
        val (dora, uraDora) = dynamicState.getDoraIndicators(table)

        // 1 槓 = 2 張指示器
        assertEquals(2, dora.size, "Should have 2 dora indicators with one kan.")
        assertEquals(2, uraDora.size, "Should have 2 ura-dora indicators with one kan.")
    }

    /**
     * 驗證當槓數達到 4 時，指示器數量應為 5（上限）。
     */
    @Test
    fun `test getDoraIndicators caps at 5 indicators with 4 or more kan`() {
        // 建立四個玩家，每人各有一個槓
        fun createPlayerWithKan(seat: Wind): MahjongPlayer {
            val kanTiles = List(4) { i ->
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, seat.ordinal + 1))
            }
            return FakeMahjongPlayerFactory.create(
                initialSeat = seat,
                hand = Hand().call(
                    type = MeldType.CLOSED_KAN,
                    tiles = kanTiles,
                    direction = RelativeDirection.Self,
                ),
            )
        }

        val players = listOf(
            createPlayerWithKan(Wind.EAST),
            createPlayerWithKan(Wind.SOUTH),
            createPlayerWithKan(Wind.WEST),
            createPlayerWithKan(Wind.NORTH),
        )

        val deadWallTiles = List(14) { i ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (i % 9) + 1))
        }
        val table = createTableStateWithWall(deadWallTiles, players)

        val dynamicState = RiichiDynamicState()
        val (dora, uraDora) = dynamicState.getDoraIndicators(table)

        assertEquals(5, dora.size, "Should cap at 5 dora indicators with 4 kan.")
        assertEquals(5, uraDora.size, "Should cap at 5 ura-dora indicators with 4 kan.")
    }

    /**
     * 驗證 getVisibleTileIds 返回的 ID 與 getDoraIndicators 的寶牌指示器一致。
     */
    @Test
    fun `test getVisibleTileIds matches dora indicator ids`() {
        val deadWallTiles = List(14) { i ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (i % 9) + 1))
        }
        val table = createTableStateWithWall(deadWallTiles)

        val dynamicState = RiichiDynamicState()
        val visibleIds = dynamicState.getVisibleTileIds(table)
        val (dora, _) = dynamicState.getDoraIndicators(table)

        val expectedIds = dora.map { it.id }.toSet()
        assertEquals(expectedIds, visibleIds, "Visible IDs should match dora indicator IDs.")
    }

    /**
     * 驗證 RiichiDynamicState 的 riichiStickCount 初始值，以及透過 copy() 產生新狀態的行為。
     */
    @Test
    fun `test riichiStickCount default and modification`() {
        val dynamicState = RiichiDynamicState()
        assertEquals(0, dynamicState.riichiStickCount)

        val updatedState = dynamicState.copy(riichiStickCount = 3)
        assertEquals(3, updatedState.riichiStickCount)
        assertEquals(0, dynamicState.riichiStickCount, "Original instance should remain unchanged.")
    }

    /**
     * 驗證 TableState.toSnapshot 搭配 RiichiDynamicState 時，牌山快照的可見牌張正確。
     */
    @Test
    fun `test table snapshot with riichi dynamic state reveals dora indicators`() {
        val deadWallTiles = List(14) { i ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (i % 9) + 1))
        }
        val observerId = Uuid.random()
        val observer = FakeMahjongPlayerFactory.create(id = observerId, initialSeat = Wind.EAST)
        val table = createTableStateWithWall(deadWallTiles, listOf(observer))
            .copy(dynamicRuleState = RiichiDynamicState())

        val snapshot = table.toSnapshot(setOf(observerId))

        // 計算應有多少張牌可見（無槓 = 1 張寶牌指示器）
        val visibleCount = snapshot.tileWall.tiles.count { it.tile != null }
        assertEquals(1, visibleCount, "Tile wall snapshot should reveal only dora indicators.")
    }
}
