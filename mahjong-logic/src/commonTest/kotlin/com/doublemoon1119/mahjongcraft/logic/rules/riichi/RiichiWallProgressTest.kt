package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.closedKanFaceDownTileIds
import com.doublemoon1119.mahjongcraft.logic.base.closedKanPubliclyRevealedTileIds
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 驗證 [RiichiWallProgress] 的寶牌與裏寶牌指示牌索引。
 *
 * 驗證寶牌指示器計算、可見牌張判定與槓數關聯邏輯。
 */
class RiichiWallProgressTest {

    /**
     * 建立一個包含指定數量王牌牌的 TableState，方便測試指示器邏輯。
     *
     * [deadWallTiles] 寫入 [TableState.reservedWallTiles]（[RiichiWallProgress.getDoraIndicators] 真正
     * 讀取的來源），不是 [TableState.tileWall]（那是活牌堆，跟王牌完全無關，見該函式 KDoc）。
     *
     * @param deadWallTiles 要放入王牌區的牌。
     * @param players 桌上的玩家；預設建立東、南兩家。
     * @return 王牌區與玩家均依參數建立的桌況。
     */
    private fun createTableStateWithWall(
        deadWallTiles: List<IdentifiedTile>,
        players: List<MahjongPlayer> = listOf(
            FakeMahjongPlayerFactory.create(Wind.EAST),
            FakeMahjongPlayerFactory.create(Wind.SOUTH),
        ),
    ): TableState {
        val config = FakeMahjongRuleConfig(
            deadTileCount = deadWallTiles.size,
        )

        return FakeTableStateFactory.create(
            players = players,
            config = config,
            reservedWallTiles = deadWallTiles,
        )
    }

    /**
     * 建立已完成指定補牌次數後仍維持十四張的死牌區。
     *
     * @param originalDeadWall 原始王牌區牌序。
     * @param completedDrawCount 已完成的補牌次數。
     * @return 移除前段補牌牌位並以測試牌補足的王牌區。
     */
    private fun afterSupplementalDraws(
        originalDeadWall: List<IdentifiedTile>,
        completedDrawCount: Int,
    ): List<IdentifiedTile> = originalDeadWall.drop(completedDrawCount) + List(completedDrawCount) {
        FakeIdentifiedTileFactory.create(Tile.Honor.Red)
    }

    /**
     * 驗證無槓時，應僅有一張寶牌指示器與一張裏寶牌指示器可見。
     */
    @Test
    fun `initial indicator selection contains one tile`() {
        val deadWallTiles = List(14) { i ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (i % 9) + 1))
        }
        val players = listOf(
            FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST),
            FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH),
        )
        val table = createTableStateWithWall(deadWallTiles, players)

        val wallProgress = RiichiWallProgress()
        val visibleIds = wallProgress.getDoraIndicators(table).first.mapTo(mutableSetOf()) { it.id }

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

        val wallProgress = RiichiWallProgress()
        val (dora, uraDora) = wallProgress.getDoraIndicators(table)

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
            hand = Hand(tiles = kanTiles.drop(1)).call(
                type = MeldType.OPEN_KAN,
                tiles = kanTiles,
                source = kanTiles[0],
                direction = RelativeDirection.Left,
                publiclyRevealedTileIds = kanTiles.map { it.id }.toSet(),
                faceDownTileIds = emptySet(),
            ),
        )

        val deadWallTiles = List(14) { i ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (i % 9) + 1))
        }
        val table = createTableStateWithWall(afterSupplementalDraws(deadWallTiles, 1), listOf(playerWithKan))

        val wallProgress = RiichiWallProgress(completedSupplementalDrawCount = 1, revealedKanDoraCount = 1)
        val (dora, uraDora) = wallProgress.getDoraIndicators(table)

        // 1 槓 = 2 張指示器
        assertEquals(2, dora.size, "Should have 2 dora indicators with one kan.")
        assertEquals(2, uraDora.size, "Should have 2 ura-dora indicators with one kan.")
        assertEquals(listOf(deadWallTiles[4], deadWallTiles[6]), dora)
        assertEquals(listOf(deadWallTiles[5], deadWallTiles[7]), uraDora)
    }

    /**
     * 驗證當槓數達到 4 時，指示器數量應為 5（上限）。
     */
    @Test
    fun `test getDoraIndicators caps at 5 indicators with 4 or more kan`() {
        // 建立四個玩家，每人各有一個槓
        /**
         * 建立指定自風、手牌含一組暗槓的測試玩家。
         *
         * @param seat 玩家自風。
         * @return 含一組暗槓的測試玩家。
         */
        fun createPlayerWithKan(seat: Wind): MahjongPlayer {
            val kanTiles = List(4) { i ->
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, seat.ordinal + 1))
            }
            return FakeMahjongPlayerFactory.create(
                initialSeat = seat,
                hand = Hand(tiles = kanTiles).call(
                    type = MeldType.CLOSED_KAN,
                    tiles = kanTiles,
                    direction = RelativeDirection.Self,
                    publiclyRevealedTileIds = closedKanPubliclyRevealedTileIds(kanTiles, revealsClosedKanTiles = true),
                    faceDownTileIds = closedKanFaceDownTileIds(kanTiles),
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
        val table = createTableStateWithWall(afterSupplementalDraws(deadWallTiles, 4), players)

        val wallProgress = RiichiWallProgress(completedSupplementalDrawCount = 4, revealedKanDoraCount = 4)
        val (dora, uraDora) = wallProgress.getDoraIndicators(table)

        assertEquals(5, dora.size, "Should cap at 5 dora indicators with 4 kan.")
        assertEquals(5, uraDora.size, "Should cap at 5 ura-dora indicators with 4 kan.")
    }
}
