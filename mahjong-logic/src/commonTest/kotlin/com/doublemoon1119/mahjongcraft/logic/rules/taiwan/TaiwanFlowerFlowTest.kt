package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.TileReplacementRounds
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 局前與途中補花、尾牌及特殊花胡續跑測試。 */
class TaiwanFlowerFlowTest {
    /** 途中已公開花牌由通用暫停點接續，補牌後仍會處理連續花牌。 */
    @Test
    fun `revealed mid-round flower resumes replacement chain`() {
        val first = flower(0)
        val second = flower(1)
        val finalReplacement = ordinary(Tile.Honor.East)
        val table = table(
            hands = listOf(emptyList(), emptyList(), emptyList(), emptyList()),
            liveTiles = listOf(ordinary(Tile.Numeric(Tile.Suit.Dot, 1)), ordinary(Tile.Numeric(Tile.Suit.Dot, 2))),
            reservedTiles = listOf(second, finalReplacement) + List(14) { ordinary(Tile.Numeric(Tile.Suit.Dot, 9)) },
            publicFlowers = listOf(listOf(first), emptyList(), emptyList(), emptyList()),
        )

        val result = assertIs<TaiwanFlowerFlow.Result.Completed>(
            TaiwanFlowerFlow.processRevealed(table, table.players[0].id, first),
        )

        assertEquals(listOf(first, second), result.tableState.players[0].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles)
        assertEquals(finalReplacement, result.tableState.players[0].hand.lastDrawn)
    }

    /** 局前逐輪補花：從莊家開始，每一家一次公開手上所有的花、補同樣張數；補到的花等下一輪再補。 */
    @Test
    fun `opening flowers go round by round`() {
        val firstFlower = flower(0)
        val secondFlower = flower(1)
        val nextPlayerFlower = flower(2)
        val firstReplacement = flower(3)
        val secondReplacement = ordinary(Tile.Honor.East)
        val thirdReplacement = ordinary(Tile.Honor.South)
        val fourthReplacement = ordinary(Tile.Honor.West)
        val table = table(
            hands = listOf(
                listOf(firstFlower, secondFlower),
                listOf(nextPlayerFlower),
                emptyList(),
                emptyList(),
            ),
            liveTiles = List(4) { ordinary(Tile.Numeric(Tile.Suit.Dot, 1)) },
            reservedTiles = listOf(firstReplacement, secondReplacement, thirdReplacement, fourthReplacement) +
                List(12) { ordinary(Tile.Numeric(Tile.Suit.Dot, 9)) },
        )
        val dealer = table.players[0].id
        val next = table.players[1].id

        val result = assertIs<TaiwanFlowerFlow.Result.Completed>(
            TaiwanFlowerFlow.process(table, table.players.map { it.id }),
        )

        assertEquals(
            listOf(firstFlower, secondFlower, firstReplacement),
            result.tableState.players[0].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles,
        )
        assertEquals(listOf(nextPlayerFlower), result.tableState.players[1].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles)
        assertEquals(fourthReplacement, result.tableState.players[0].hand.lastDrawn)
        assertEquals(thirdReplacement, result.tableState.players[1].hand.lastDrawn)
        assertEquals(
            listOf(
                TileReplacementRounds.Step.SetAside(dealer, listOf(firstFlower, secondFlower)),
                TileReplacementRounds.Step.Replaced(dealer, listOf(firstReplacement, secondReplacement)),
                TileReplacementRounds.Step.SetAside(next, listOf(nextPlayerFlower)),
                TileReplacementRounds.Step.Replaced(next, listOf(thirdReplacement)),
                TileReplacementRounds.Step.SetAside(dealer, listOf(firstReplacement)),
                TileReplacementRounds.Step.Replaced(dealer, listOf(fourthReplacement)),
            ),
            result.steps,
        )
    }

    /** 最後一張活牌仍可補一次，再次遇到花時公開花牌並流局。 */
    @Test
    fun `last live tile supports one replacement and then exhausts`() {
        val first = flower(0)
        val replacement = flower(1)
        val table = table(
            hands = listOf(listOf(first), emptyList(), emptyList(), emptyList()),
            liveTiles = listOf(ordinary(Tile.Numeric(Tile.Suit.Dot, 1))),
            reservedTiles = listOf(replacement) + List(15) { ordinary(Tile.Numeric(Tile.Suit.Dot, 9)) },
        )

        val result = assertIs<TaiwanFlowerFlow.Result.Exhausted>(
            TaiwanFlowerFlow.process(table, listOf(table.players[0].id)),
        )

        assertEquals(table.players[0].id, result.playerId)
        assertEquals(listOf(first, replacement), result.tableState.players[0].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles)
        assertEquals(16, result.tableState.reservedWallTiles.size)
    }

    /** 七花玩家放過別家最後一花時，由亮花者照常取得補牌，並把這次補牌回報為呈現步驟。 */
    @Test
    fun `passing seven flowers resumes replacement for the revealer`() {
        val flowers = flowerTiles()
        val replacement = ordinary(Tile.Honor.East)
        val table = table(
            hands = listOf(emptyList(), listOf(flowers.last()), emptyList(), emptyList()),
            liveTiles = listOf(ordinary(Tile.Numeric(Tile.Suit.Dot, 1))),
            reservedTiles = listOf(replacement) + List(15) { ordinary(Tile.Numeric(Tile.Suit.Dot, 9)) },
            publicFlowers = listOf(flowers.take(7), emptyList(), emptyList(), emptyList()),
        )
        val awaiting = assertIs<TaiwanFlowerFlow.Result.AwaitingDecision>(
            TaiwanFlowerFlow.process(table, listOf(table.players[1].id)),
        )

        val result = assertIs<TaiwanFlowerFlow.Result.Completed>(TaiwanFlowerFlow.resolve(awaiting.pending, accepted = false))

        assertEquals(replacement, result.tableState.players[1].hand.lastDrawn)
        assertEquals(7, result.tableState.players[0].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles?.size)
        assertEquals(1, result.tableState.players[1].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles?.size)
        assertEquals(listOf(TileReplacementRounds.Step.Replaced(table.players[1].id, listOf(replacement))), result.steps)
    }

    /** 七花玩家接受時被搶的花留在亮花者花區、不另外補牌，只有亮花者付款。 */
    @Test
    fun `accepting seven flowers keeps the claimed flower in place without a replacement`() {
        val flowers = flowerTiles()
        val replacement = ordinary(Tile.Honor.East)
        val table = table(
            hands = listOf(emptyList(), listOf(flowers.last()), emptyList(), emptyList()),
            liveTiles = listOf(ordinary(Tile.Numeric(Tile.Suit.Dot, 1))),
            reservedTiles = listOf(replacement) + List(15) { ordinary(Tile.Numeric(Tile.Suit.Dot, 9)) },
            publicFlowers = listOf(flowers.take(7), emptyList(), emptyList(), emptyList()),
        )
        val awaiting = assertIs<TaiwanFlowerFlow.Result.AwaitingDecision>(
            TaiwanFlowerFlow.process(table, listOf(table.players[1].id)),
        )

        val result = assertIs<TaiwanFlowerFlow.Result.Won>(TaiwanFlowerFlow.resolve(awaiting.pending, accepted = true))

        assertEquals(7, result.tableState.players[0].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles?.size)
        assertEquals(listOf(flowers.last()), result.tableState.players[1].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles)
        assertEquals(flowers.last(), result.winningFlower)
        assertEquals(awaiting.pending.tableState.tileWall, result.tableState.tileWall)
        assertEquals(awaiting.pending.tableState.reservedWallTiles, result.tableState.reservedWallTiles)
        assertEquals(setOf(table.players[1].id), result.settlement.paymentsByPlayerId.keys)
    }

    /** 已沒有合法補牌時，別家亮出最後一花只會公開並流局，不開放七搶一。 */
    @Test
    fun `seven flowers is not offered when no replacement remains`() {
        val flowers = flowerTiles()
        val table = table(
            hands = listOf(emptyList(), listOf(flowers.last()), emptyList(), emptyList()),
            liveTiles = emptyList(),
            publicFlowers = listOf(flowers.take(7), emptyList(), emptyList(), emptyList()),
        )

        val result = assertIs<TaiwanFlowerFlow.Result.Exhausted>(TaiwanFlowerFlow.process(table, listOf(table.players[1].id)))

        assertEquals(table.players[1].id, result.playerId)
        assertEquals(listOf(flowers.last()), result.tableState.players[1].tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles)
    }

    /** 建立具有固定保留區與指定手牌、活牌的四人桌況。 */
    private fun table(
        hands: List<List<IdentifiedTile>>,
        liveTiles: List<IdentifiedTile>,
        publicFlowers: List<List<IdentifiedTile>> = List(4) { emptyList() },
        reservedTiles: List<IdentifiedTile> = List(16) { ordinary(Tile.Numeric(Tile.Suit.Dot, 9)) },
    ) = FakeTableStateFactory.create(
        players = Wind.entries.mapIndexed { index, wind ->
            FakeMahjongPlayerFactory.create(wind).copy(
                hand = Hand(tiles = hands[index]),
                tileAreas = PlayerTileAreas.EMPTY.withArea(TaiwanPlayerTileAreaIds.FLOWERS, publicFlowers[index]),
            )
        },
        config = TaiwanRuleConfig(),
        tileWall = TileWall(liveTiles),
        reservedWallTiles = reservedTiles,
        turnPhase = TurnPhase.AwaitingAction,
    )

    /** 依固定索引建立一張花牌。 */
    private fun flower(index: Int): IdentifiedTile = flowerTiles()[index]

    /** 建立八種花牌。 */
    private fun flowerTiles(): List<IdentifiedTile> = TaiwanTileTypes.ALL.map { ordinary(Tile.Extension(it)) }

    /** 建立一張指定牌面的牌。 */
    private fun ordinary(tile: Tile): IdentifiedTile = FakeIdentifiedTileFactory.create(tile)
}
