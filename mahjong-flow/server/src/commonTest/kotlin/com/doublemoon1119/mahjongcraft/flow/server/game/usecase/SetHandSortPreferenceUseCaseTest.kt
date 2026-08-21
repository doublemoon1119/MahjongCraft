package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [SetHandSortPreferenceUseCase] 的單元測試類別。
 *
 * 驗證偏好本身正確記錄、只在玩家目前坐在對局中且沒有還沒決定的摸牌（`Hand.lastDrawn == null`）時
 * 才立即整理手牌並重新呈現。
 */
class SetHandSortPreferenceUseCaseTest {

    private val gameId = Uuid.random()
    private val playerId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val preferenceStore = HandSortPreferenceStore()
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase = SetHandSortPreferenceUseCase(gameRepo, moduleRegistry, preferenceStore, presentationPublisher)
    }

    private val sortedLastTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
    private val unsortedTiles = listOf(
        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5)),
        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 3)),
    )

    /** 停用時只更新偏好本身，不觸碰手牌，也不重新呈現。 */
    @Test
    fun `test disabling only records the preference`() = runTest {
        val fixtures = Fixtures()
        fixtures.preferenceStore.set(playerId, true)

        fixtures.useCase(playerId, false)

        assertFalse(fixtures.preferenceStore.isEnabled(playerId))
        assertNull(fixtures.presentationPublisher.getPublishedPlayerArea(gameId))
    }

    /** 啟用且玩家目前沒有還沒決定的摸牌時，立即整理手牌並重新呈現這個座位。 */
    @Test
    fun `test enabling organizes hand immediately when there is no pending draw`() = runTest {
        val fixtures = Fixtures()
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = unsortedTiles),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(playerId, true)

        assertTrue(fixtures.preferenceStore.isEnabled(playerId))
        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        val organizedTiles = newState.players.first { it.id == playerId }.hand.tiles
        assertEquals(unsortedTiles.sortedBy { it.tile.toString() }.size, organizedTiles.size)
        assertNotNull(fixtures.presentationPublisher.getPublishedPlayerArea(gameId))
    }

    /** 啟用但玩家手上還有一張尚未決定的摸牌時，這次先不整理，不打斷還沒做的決定。 */
    @Test
    fun `test enabling does not organize when a draw is still pending`() = runTest {
        val fixtures = Fixtures()
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = unsortedTiles, lastDrawn = sortedLastTile),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(playerId, true)

        assertTrue(fixtures.preferenceStore.isEnabled(playerId))
        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        val unchangedPlayer = newState.players.first { it.id == playerId }
        assertEquals(unsortedTiles, unchangedPlayer.hand.tiles)
        assertEquals(sortedLastTile, unchangedPlayer.hand.lastDrawn)
        assertNull(fixtures.presentationPublisher.getPublishedPlayerArea(gameId))
    }
}
