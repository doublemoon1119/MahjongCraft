package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTileAudience
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTilePresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.history.HistoryTilePresentationResolver
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 驗證交易提交時保存的牌張呈現：開局列出所有牌，之後只列出改變的牌，且位於桌況結果之前。 */
class AuthoritativeStateStoreTilePresentationTest {
    /** 開局列出全部牌；摸牌只記錄被摸的那張，並排在桌況結果前面。 */
    @Test
    fun `opening lists every tile and later transactions list only changed tiles`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, tilePresentationResolver = WallAndHandResolver)
        val wallTiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val before = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(hand = Hand())),
            tileWall = TileWall(wallTiles),
        )
        val drawn = before.tileWall.draw()
        val drawnTile = checkNotNull(drawn.tile)
        val after = before.copy(
            tileWall = drawn.wall,
            players = before.players.map { it.copy(hand = Hand(lastDrawn = drawnTile)) },
        )
        val game = store.openGame(before)

        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (game.id to game.copy(tableState = after))),
                result = Unit,
                historyDraftsByVenueId = mapOf(game.id to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom))),
            )
        }

        val facts = store.snapshot().historyRecordingState.pendingEvents.map { it.fact }
        val opening = assertIs<HistoryFact.TilePresentationChanged>(facts[1])
        assertEquals(wallTiles.map { it.id }.toSet(), opening.presentations.keys)
        val changeIndex = facts.indexOfFirst { it is HistoryFact.TilePresentationChanged && it !== opening }
        val change = assertIs<HistoryFact.TilePresentationChanged>(facts[changeIndex])
        assertEquals(mapOf(drawnTile.id to presentation(TilePose.UPRIGHT)), change.presentations)
        assertIs<HistoryFact.TableChanged>(facts[changeIndex + 1])
    }

    /** 牌山的牌背面朝上、手牌直立的測試解算器。 */
    private object WallAndHandResolver : HistoryTilePresentationResolver {
        override fun resolve(tableState: TableState): Map<Uuid, HistoryTilePresentation> = tableState.tileWall.getAllTiles().associate { it.id to presentation(TilePose.FACE_DOWN) } +
            tableState.players.flatMap { it.hand.standingTiles }.associate { it.id to presentation(TilePose.UPRIGHT) }
    }
}

/**
 * 建立測試呈現結果。
 *
 * @param pose 共同姿態。
 * @return 只有姿態不同的呈現結果。
 */
private fun presentation(pose: TilePose): HistoryTilePresentation = HistoryTilePresentation(null, ComponentViewSet.EMPTY, pose, HistoryTileAudience.Players(emptySet()))
