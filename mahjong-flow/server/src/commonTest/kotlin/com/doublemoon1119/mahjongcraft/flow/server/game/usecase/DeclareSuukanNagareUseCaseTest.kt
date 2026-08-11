package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [DeclareSuukanNagareUseCase] 的單元測試類別。
 *
 * 驗證四槓散了的觸發判斷（交給 `MahjongRuleModule.resolveSuukanNagare`）、`ExhaustiveDraw` 記錄進
 * 全員（不只莊家）的 `actionHistory`、途中流局不結算任何點數、快照與事件的同步行為（actor 為莊家
 * Uuid，比照 `DeclareExhaustiveDrawUseCase` 既有慣例），以及各種驗證失敗案例。
 */
class DeclareSuukanNagareUseCaseTest {

    private val gameId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val eventPublisher = FakeGameEventPublisher()
        val useCase = DeclareSuukanNagareUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher)
    }

    private fun kanMeld(): Meld {
        val tile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        return Meld(MeldType.CLOSED_KAN, listOf(tile, tile, tile, tile), sourceDirection = RelativeDirection.Self)
    }

    private fun playerWithKans(id: Uuid = Uuid.random(), initialSeat: Wind, kanCount: Int) = FakeMahjongPlayerFactory.create(
        id = id,
        initialSeat = initialSeat,
        hand = Hand(melds = List(kanCount) { kanMeld() }),
    ).copy(score = 25000)

    /**
     * 驗證 4 個槓子分屬不同玩家時：全員 `actionHistory` 皆記錄 `ExhaustiveDraw(SuukanNagare)`、
     * 分數皆不變（途中流局不結算任何點數）。
     */
    @Test
    fun `test declare suukan nagare records ExhaustiveDraw for all players and does not change scores`() = runTest {
        val fixtures = Fixtures()
        val dealerId = Uuid.random()
        val players = listOf(
            playerWithKans(id = dealerId, initialSeat = Wind.EAST, kanCount = 1),
            playerWithKans(initialSeat = Wind.SOUTH, kanCount = 1),
            playerWithKans(initialSeat = Wind.WEST, kanCount = 1),
            playerWithKans(initialSeat = Wind.NORTH, kanCount = 1),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = players, config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val expectedAction = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SuukanNagare)
        newState.players.forEach { player ->
            assertEquals(expectedAction, player.actionHistory.last(), "Every player should have ExhaustiveDraw recorded, not just the dealer.")
            assertEquals(25000, player.score, "Suukan nagare is an abortive draw and should not exchange any points.")
        }
    }

    /**
     * 驗證 4 個槓子都由同一位玩家達成時，宣告不合法。
     */
    @Test
    fun `test declare suukan nagare fails when all 4 kans belong to a single player`() = runTest {
        val fixtures = Fixtures()
        val players = listOf(
            playerWithKans(initialSeat = Wind.EAST, kanCount = 4),
            FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH),
            FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST),
            FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = players, config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.UnsupportedAction(gameId), result.error)
    }

    /**
     * 驗證未滿 4 個槓子時，宣告不合法。
     */
    @Test
    fun `test declare suukan nagare fails when fewer than 4 kans total`() = runTest {
        val fixtures = Fixtures()
        val players = listOf(
            playerWithKans(initialSeat = Wind.EAST, kanCount = 1),
            playerWithKans(initialSeat = Wind.SOUTH, kanCount = 1),
            FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST),
            FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = players, config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.UnsupportedAction(gameId), result.error)
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test declare suukan nagare fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /**
     * 驗證宣告成功後所有觀察者的快照皆同步更新，且所有玩家皆收到事件通知（actor 為莊家本人）。
     */
    @Test
    fun `test declare suukan nagare syncs snapshot and notifies all players with dealer as actor`() = runTest {
        val fixtures = Fixtures()
        val dealerId = Uuid.random()
        val otherId = Uuid.random()
        val players = listOf(
            playerWithKans(id = dealerId, initialSeat = Wind.EAST, kanCount = 1),
            playerWithKans(id = otherId, initialSeat = Wind.SOUTH, kanCount = 1),
            playerWithKans(initialSeat = Wind.WEST, kanCount = 1),
            playerWithKans(initialSeat = Wind.NORTH, kanCount = 1),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = players, config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(dealerId, table.toSnapshot(setOf(dealerId)))
        fixtures.snapshotRepo.setSnapshot(otherId, table.toSnapshot(setOf(otherId)))

        fixtures.useCase(gameId)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, dealerId))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, otherId))
        val expectedAction = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SuukanNagare)
        assertEquals(expectedAction, fixtures.eventPublisher.getNotifiedAction(gameId, otherId, dealerId))
    }
}
