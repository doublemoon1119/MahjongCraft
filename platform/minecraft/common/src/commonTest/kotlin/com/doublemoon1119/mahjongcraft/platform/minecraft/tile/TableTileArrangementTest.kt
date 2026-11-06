package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證整桌擺放與牌區擺放一致，並把捨牌與摸牌後的變化分到正確的排。 */
class TableTileArrangementTest {
    /** 立牌、摸牌位與牌河各自成排，位置與單一牌區擺放的結果相同。 */
    @Test
    fun `arrangement places hand, drawn slot and discards in their rows`() {
        val standing = List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, it + 1)) }
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val discarded = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val player = FakeMahjongPlayerFactory.create(Wind.EAST, hand = Hand(tiles = standing, lastDrawn = drawn), discardPile = FakeDiscardPile().discardTile(discarded))
        val state = FakeTableStateFactory.create(players = listOf(player, FakeMahjongPlayerFactory.create(Wind.SOUTH)), turnPhase = TurnPhase.AwaitingAction)

        val arrangement = TableTileArrangement.arrange(input(state))

        val area = MahjongPlayerAreaLayout.compute(
            MahjongPlayerAreaLayoutInput(
                controllerX = 0,
                controllerY = 64,
                controllerZ = 0,
                tableFacing = MahjongTableFacing.NORTH,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(2),
                seatIndex = 0,
                standingTileIds = standing.map { it.id },
                drawnTileId = drawn.id,
                melds = emptyList(),
                setAsideTileIds = emptyList(),
                cornerWidth = 0.0,
            ),
        )
        area.standing.forEach { placed -> assertEquals(placed.placement, arrangement.getValue(placed.tileId).placement) }
        assertEquals(area.drawnSlot, arrangement.getValue(drawn.id).placement)
        assertEquals(TileRowKey(TileRegion.DRAWN, player.id, null), arrangement.getValue(drawn.id).row)
        assertEquals(TileRowKey(TileRegion.DISCARD, player.id, null), arrangement.getValue(discarded.id).row)
        assertEquals(TilePose.FACE_UP, arrangement.getValue(discarded.id).pose)
        standing.forEach { tile -> assertEquals(TileRowKey(TileRegion.HAND, player.id, null), arrangement.getValue(tile.id).row) }
    }

    /** 立牌的姿態來自規則提供的逐張姿態，沒有提供時直立。 */
    @Test
    fun `hand poses follow the supplied pose states`() {
        val (laid, upright) = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.Red) }
        val player = FakeMahjongPlayerFactory.create(Wind.EAST, hand = Hand(tiles = listOf(laid, upright)))
        val state = FakeTableStateFactory.create(players = listOf(player, FakeMahjongPlayerFactory.create(Wind.SOUTH)))

        val arrangement = TableTileArrangement.arrange(input(state).copy(handPoseStates = listOf(TilePoseState(laid.id, TilePose.FACE_UP))))

        assertEquals(TilePose.FACE_UP, arrangement.getValue(laid.id).pose)
        assertEquals(TilePose.UPRIGHT, arrangement.getValue(upright.id).pose)
    }

    /** 打出立牌中的牌：捨出的牌飛向牌河，摸牌位的牌以弧線落入立牌，其餘立牌平移補位，三者在同一步。 */
    @Test
    fun `discarding a hand tile flies the discard, arcs the drawn tile in and slides the rest`() {
        val standing = List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, it + 1)) }
        val drawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9))
        val player = FakeMahjongPlayerFactory.create(Wind.EAST, hand = Hand(tiles = standing, lastDrawn = drawn), discardPile = FakeDiscardPile())
        val before = FakeTableStateFactory.create(players = listOf(player, FakeMahjongPlayerFactory.create(Wind.SOUTH)), turnPhase = TurnPhase.AwaitingAction)
        val discarded = standing[1]
        val after = before.copy(
            players = before.players.map {
                if (it.id != player.id) it else it.copy(hand = Hand(tiles = standing - discarded + drawn), discardPile = it.discardPile.discardTile(discarded))
            },
            turnPhase = TurnPhase.AwaitingDraw,
        )

        val motions = TableStepMotionPlanner.plan(TableTileArrangement.arrange(input(before)), TableTileArrangement.arrange(input(after)))
        val kinds = motions.associate { it.tileId to it.kind }

        assertEquals(TileMotionKind.FLIGHT, kinds[discarded.id])
        assertEquals(TileMotionKind.ARC, kinds[drawn.id])
        assertTrue(kinds.filterKeys { it != discarded.id && it != drawn.id }.values.all { it == TileMotionKind.SLIDE || it == TileMotionKind.ARC })
    }

    /**
     * 直接補進立牌的牌依補進順序接在立牌最右邊，摸牌位的牌往旁邊挪過去；玩家理牌時，這些牌越過其他牌插進該在的位置。
     */
    @Test
    fun `replacement tiles wait at the right end of the hand until it is sorted`() {
        val standing = List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, it + 1)) }
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val replacements = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.North) }
        val player = FakeMahjongPlayerFactory.create(Wind.EAST, hand = Hand(tiles = standing, lastDrawn = drawn))
        val before = FakeTableStateFactory.create(players = listOf(player, FakeMahjongPlayerFactory.create(Wind.SOUTH)), turnPhase = TurnPhase.AwaitingAction)
        // 補牌之後，原本摸牌位的牌與第一張補牌併入立牌，第二張補牌在摸牌位。
        val after = before.copy(players = before.players.map { if (it.id != player.id) it else it.copy(hand = Hand(tiles = standing + drawn + replacements[0], lastDrawn = replacements[1])) })
        val previous = TableTileArrangement.arrange(input(before)) + replacements.associate { tile -> tile.id to inWall(tile.id) }

        val unsorted = UnsortedHandTiles.after(previous, after, sortedPlayerIds = emptySet())
        val waiting = TableTileArrangement.arrange(input(after).copy(unsortedTileIds = unsorted))

        assertEquals(listOf(drawn.id, replacements[0].id), unsorted)
        val handOrder = waiting.values.filter { it.row.region == TileRegion.HAND }.sortedBy { it.orderInRow }.map { it.tileId }
        assertEquals(listOf(drawn.id, replacements[0].id), handOrder.takeLast(2), "Unsorted tiles stand at the right end in arrival order.")
        assertTrue(waiting.getValue(drawn.id).unsorted)
        assertEquals(TileMotionKind.SLIDE, TableStepMotionPlanner.plan(previous, waiting).single { it.tileId == drawn.id }.kind)

        val sortedAfter = TableTileArrangement.arrange(input(after).copy(unsortedTileIds = UnsortedHandTiles.after(waiting, after, setOf(player.id))))
        val sortMotions = TableStepMotionPlanner.plan(waiting, sortedAfter)

        assertTrue(sortedAfter.values.none { it.unsorted })
        assertTrue(sortMotions.filter { it.tileId in setOf(drawn.id, replacements[0].id) }.all { it.kind == TileMotionKind.ARC || it.kind == TileMotionKind.SLIDE })
        assertTrue(sortMotions.any { it.kind == TileMotionKind.ARC }, "Sorting carries the unsorted tiles over the hand.")
    }

    /** 玩家打出牌時正在整理手牌：摸牌位的牌直接理進去，不會留在最右邊。 */
    @Test
    fun `discarding sorts the drawn tile straight into the hand`() {
        val standing = List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, it + 1)) }
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.West)
        val player = FakeMahjongPlayerFactory.create(Wind.EAST, hand = Hand(tiles = standing, lastDrawn = drawn), discardPile = FakeDiscardPile())
        val before = FakeTableStateFactory.create(players = listOf(player, FakeMahjongPlayerFactory.create(Wind.SOUTH)), turnPhase = TurnPhase.AwaitingAction)
        val after = before.copy(
            players = before.players.map {
                if (it.id != player.id) it else it.copy(hand = Hand(tiles = standing - standing[0] + drawn), discardPile = it.discardPile.discardTile(standing[0]))
            },
        )

        assertEquals(emptyList(), UnsortedHandTiles.after(TableTileArrangement.arrange(input(before)), after, sortedPlayerIds = emptySet()))
    }

    /** 沒有實體牌牆的桌況不擺放牌牆的牌。 */
    @Test
    fun `wall tiles are skipped without a wall projection`() {
        val state = FakeTableStateFactory.create()

        val arrangement = TableTileArrangement.arrange(input(state))

        assertTrue(state.tileWall.getAllTiles().none { it.id in arrangement })
    }

    /**
     * 還在牌牆上的牌。
     *
     * @param tileId 牌的識別碼。
     * @return 牌牆那一排中的擺放。
     */
    private fun inWall(tileId: Uuid) = ArrangedTile(
        tileId = tileId,
        row = TileRowKey(TileRegion.WALL, null, null),
        orderInRow = 0,
        placement = MahjongTileWallPlacement(x = 0.0, y = 64.0, z = 0.0, yaw = 0.0f),
        pose = TilePose.FACE_DOWN,
    )

    /** 北向、兩人桌、沒有牌牆投影的擺放輸入。 */
    private fun input(state: TableState) = TableTileArrangementInput(
        controllerX = 0,
        controllerY = 64,
        controllerZ = 0,
        tableFacing = MahjongTableFacing.NORTH,
        seatLayout = MahjongTableSeatLayout.forPlayerCount(state.players.size),
        tableState = state,
        cornerWidthBySeat = emptyMap(),
        handPoseStates = emptyList(),
        wallProjection = null,
        faceUpWallTileIds = emptySet(),
    )
}
