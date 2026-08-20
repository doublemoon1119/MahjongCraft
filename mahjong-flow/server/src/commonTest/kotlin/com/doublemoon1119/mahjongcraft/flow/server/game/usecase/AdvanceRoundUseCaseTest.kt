package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [AdvanceRoundUseCase] 的單元測試類別。
 *
 * 驗證連莊/過莊判定、開下一局、整場對局結束時的過渡行為，以及快照與事件的同步行為。
 */
class AdvanceRoundUseCaseTest {

    private val gameId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase =
            AdvanceRoundUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher)
    }

    /**
     * 驗證莊家胡牌時，連莊：本場數 +1、莊家不變、局數不變，且確實重新發了一手新牌、清空了
     * 每局狀態。
     */
    @Test
    fun `test advance round repeats dealer when dealer won`() = runTest {
        val fixtures = Fixtures()
        val dealerId = Uuid.random()
        val dealer = FakeMahjongPlayerFactory.create(Wind.EAST, id = dealerId)
            .copy(actionHistory = listOf(GameAction.Tsumo))
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val players = listOf(dealer, p2, p3, p4)
        val config = RiichiRuleConfig()
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = players,
            config = config,
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )
        val remainingReserveMillisByPlayerId = players.associate { it.id to 12_345L }
        fixtures.gameRepo.setGame(
            Game(
                tableState = table,
                flowConfig = GameFlowConfig(),
                remainingReserveMillisByPlayerId = remainingReserveMillisByPlayerId,
            ),
        )

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val advanceResult = result.value
        assertEquals(false, advanceResult.isMatchOver)
        val newState = advanceResult.tableState
        assertEquals(1, newState.comboCount)
        assertEquals(1, newState.roundNumber)
        assertEquals(Wind.EAST, newState.prevalentWind)
        assertEquals(
            remainingReserveMillisByPlayerId,
            fixtures.gameRepo.getGame(gameId)?.remainingReserveMillisByPlayerId,
        )
        assertEquals(
            dealerId,
            newState.players.first { it.currentWind == Wind.EAST }.id,
            "Dealer should stay the same on a repeat.",
        )
        newState.players.forEach { player ->
            assertEquals(
                config.initialHandSize,
                player.hand.tiles.size,
                "A fresh hand should be dealt for the new round.",
            )
            assertTrue(player.actionHistory.isEmpty(), "actionHistory should be reset for the new round.")
        }
        assertNull(newState.pendingReaction)
    }

    /**
     * 驗證非莊家胡牌時，過莊：本場數歸零、局數 +1、莊家換成座位順序中的下一位。
     */
    @Test
    fun `test advance round rotates dealer when dealer did not win`() = runTest {
        val fixtures = Fixtures()
        val dealer = FakeMahjongPlayerFactory.create(Wind.EAST)
        val nextDealerId = Uuid.random()
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH, id = nextDealerId)
            .copy(actionHistory = listOf(GameAction.Ron(Uuid.random())))
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, p2, p3, p4),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            roundNumber = 1,
            comboCount = 2,
            prevalentWind = Wind.EAST,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val advanceResult = result.value
        assertEquals(false, advanceResult.isMatchOver)
        val newState = advanceResult.tableState
        assertEquals(0, newState.comboCount)
        assertEquals(2, newState.roundNumber)
        assertEquals(Wind.EAST, newState.prevalentWind)
        assertEquals(
            nextDealerId,
            newState.players.first { it.currentWind == Wind.EAST }.id,
            "P2 (South) should become the new dealer.",
        )
    }

    /**
     * 驗證莊家一般流局時聽牌（`actionHistory` 有 `ExhaustiveDraw`）：連莊、本場數 +1、莊家不變。
     */
    @Test
    fun `test advance round repeats dealer when dealer was tenpai at exhaustive draw`() = runTest {
        val fixtures = Fixtures()
        val dealerId = Uuid.random()
        val dealer = FakeMahjongPlayerFactory.create(Wind.EAST, id = dealerId)
            .copy(actionHistory = listOf(GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.Normal)))
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, p2, p3, p4),
            config = RiichiRuleConfig(),
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val advanceResult = result.value
        assertEquals(false, advanceResult.isMatchOver)
        val newState = advanceResult.tableState
        assertEquals(1, newState.comboCount)
        assertEquals(1, newState.roundNumber)
        assertEquals(
            dealerId,
            newState.players.first { it.currentWind == Wind.EAST }.id,
            "Dealer should stay the same when tenpai at exhaustive draw.",
        )
    }

    /**
     * 驗證莊家一般流局時不聽（`actionHistory` 沒有 Tsumo/Ron/ExhaustiveDraw）：過莊、換下一位當莊家。
     */
    @Test
    fun `test advance round rotates dealer when dealer was noten at exhaustive draw`() = runTest {
        val fixtures = Fixtures()
        val dealer = FakeMahjongPlayerFactory.create(Wind.EAST)
        val nextDealerId = Uuid.random()
        // 莊家不聽時 actionHistory 完全空白（DeclareExhaustiveDrawUseCase 只把 ExhaustiveDraw
        // 記錄進聽牌玩家），p2 是否記錄跟這個判斷式無關，這裡刻意留空模擬「p2 也不聽」的情境。
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH, id = nextDealerId)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, p2, p3, p4),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            roundNumber = 1,
            comboCount = 2,
            prevalentWind = Wind.EAST,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val advanceResult = result.value
        assertEquals(false, advanceResult.isMatchOver)
        val newState = advanceResult.tableState
        assertEquals(0, newState.comboCount)
        assertEquals(2, newState.roundNumber)
        assertEquals(
            nextDealerId,
            newState.players.first { it.currentWind == Wind.EAST }.id,
            "P2 (South) should become the new dealer.",
        )
    }

    /**
     * 驗證局數已達 [GameLength.totalRounds] 上限時，
     * 回報整場對局已結束，且不會開新的一局（回傳與寫回的桌況都與呼叫前完全相同）。
     */
    @Test
    fun `test advance round reports match over without starting a new round`() = runTest {
        val fixtures = Fixtures()
        val dealer = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, p2, p3, p4),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.OneGame),
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val advanceResult = result.value
        assertEquals(true, advanceResult.isMatchOver)
        assertEquals(
            table,
            advanceResult.tableState,
            "Table state should be left completely untouched when the match is over.",
        )
        assertEquals(table, fixtures.gameRepo.getTableState(gameId), "Repository should not have been mutated either.")
    }

    /**
     * 驗證開新的一局後，所有觀察者的快照皆同步更新，且所有玩家皆收到 [GameAction.RoundStarted] 事件通知。
     */
    @Test
    fun `test advance round syncs snapshot and notifies all players`() = runTest {
        val fixtures = Fixtures()
        val dealerId = Uuid.random()
        val dealer = FakeMahjongPlayerFactory.create(Wind.EAST, id = dealerId)
            .copy(actionHistory = listOf(GameAction.Tsumo))
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, p2, p3, p4),
            config = RiichiRuleConfig(),
        )
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(dealerId, table.toSnapshot(setOf(dealerId)))
        fixtures.snapshotRepo.setSnapshot(p2.id, table.toSnapshot(setOf(p2.id)))

        fixtures.useCase(gameId)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, dealerId))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, p2.id))
        assertTrue(GameAction.RoundStarted in fixtures.eventPublisher.getNotifiedActions(gameId, dealerId, dealerId))
        assertTrue(GameAction.RoundStarted in fixtures.eventPublisher.getNotifiedActions(gameId, p2.id, dealerId))
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test advance round fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }
}
