package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiTurnDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ForcedAutoPlayDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameActionRouter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKyuushuKyuuhaiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareSuukanNagareUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToChankanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * [MahjongAutoDrawService] 的單元測試類別。
 *
 * 用真正的 [GameFlowCoordinator]（比照 `GameFlowCoordinatorTest` 的既有 Fixtures 寫法）驗證
 * 判斷邏輯：真人且尚未摸牌時會實際摸到一張牌；AI 或已經摸過牌時不會重複摸牌。
 */
class MahjongAutoDrawServiceTest {

    private class Fixtures {
        val store = AuthoritativeStateStore()
        val gameRepo = GameRepositoryImpl(store)
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val router = GameActionRouter(
            drawTileUseCase = DrawTileUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            discardTileUseCase = DiscardTileUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            declareRiichiUseCase = DeclareRiichiUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            declareTsumoUseCase = DeclareTsumoUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            declareKanUseCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            respondToDiscardUseCase = RespondToDiscardUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            respondToChankanUseCase = RespondToChankanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            declareKyuushuKyuuhaiUseCase = DeclareKyuushuKyuuhaiUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
        )
        val getLegalActionsUseCase = GetLegalActionsUseCase(gameRepo, moduleRegistry)
        val aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = RandomAiStrategy.KEY).apply { registerBuiltInAiStrategies() }
        val aiTurnDriver = AiTurnDriver(gameRepo, getLegalActionsUseCase, aiStrategyRegistry, GameVisibilityPolicyImpl())
        val clock = MutableMonotonicClock()
        val decisionTimerManager = GameDecisionTimerManager(
            gameRepository = gameRepo,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(clock),
            clock = clock,
        )
        val coordinator = GameFlowCoordinator(
            gameActionRouter = router,
            gameRepository = gameRepo,
            moduleRegistry = moduleRegistry,
            declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            declareSuukanNagareUseCase = DeclareSuukanNagareUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            advanceRoundUseCase = AdvanceRoundUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
            aiTurnDriver = aiTurnDriver,
            forcedAutoPlayDriver = ForcedAutoPlayDriver(gameRepo),
            decisionTimerManager = decisionTimerManager,
            decisionTimerSynchronizationService = DecisionTimerSynchronizationService(
                decisionTimerManager,
                gameRepo,
                FakeDecisionTimerUpdatePublisher(),
            ),
        )
        val autoDrawService = MahjongAutoDrawService(gameRepo, coordinator)
    }

    /** 提供固定時間的假時鐘，避免測試依賴真實系統時間。 */
    private class MutableMonotonicClock : MonotonicClock {
        override fun nowMillis(): Long = 0L
    }

    /** 建立一場已開局、尚未有任何玩家摸牌的真局；莊家（東家）維持真人。 */
    private suspend fun Fixtures.createStartedGame(dealerIsAi: Boolean = false): Uuid {
        val playerIds = List(4) { Uuid.random() }
        val module = moduleRegistry.getModule(RiichiRuleConfig())
        val gameId = Uuid.random()
        val result = GameInitializer.initialize(
            id = gameId,
            playerIds = playerIds,
            module = module,
            aiPlayerStrategyKeys = if (dealerIsAi) playerIds.associateWith { RandomAiStrategy.KEY } else emptyMap(),
        )
        gameRepo.setTableState(result.tableState)
        return gameId
    }

    /** 驗證真人莊家尚未摸牌時，`checkAndAutoDraw` 會實際幫他摸到一張牌。 */
    @Test
    fun `test checkAndAutoDraw draws for human player who has not drawn yet`() = runTest {
        val fixtures = Fixtures()
        val gameId = fixtures.createStartedGame()

        fixtures.autoDrawService.checkAndAutoDraw(gameId)

        val state = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(state)
        assertNotNull(state.currentPlayer.hand.lastDrawn, "Human dealer should have been auto-drawn a tile.")
    }

    /** 驗證目前玩家是 AI 時，`checkAndAutoDraw` 不會介入（交給 `driveAutomatedPlayers` 處理）。 */
    @Test
    fun `test checkAndAutoDraw skips when current player is ai`() = runTest {
        val fixtures = Fixtures()
        val gameId = fixtures.createStartedGame(dealerIsAi = true)

        fixtures.autoDrawService.checkAndAutoDraw(gameId)

        val state = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(state)
        assertNull(state.currentPlayer.hand.lastDrawn, "AI dealer should not be auto-drawn by MahjongAutoDrawService.")
    }

    /** 驗證已經摸過牌的玩家再次呼叫時不會重複摸牌。 */
    @Test
    fun `test checkAndAutoDraw does not draw twice for the same turn`() = runTest {
        val fixtures = Fixtures()
        val gameId = fixtures.createStartedGame()

        fixtures.autoDrawService.checkAndAutoDraw(gameId)
        val remainingAfterFirstDraw = fixtures.gameRepo.getTableState(gameId)?.tileWall?.remainingCount

        fixtures.autoDrawService.checkAndAutoDraw(gameId)
        val remainingAfterSecondCall = fixtures.gameRepo.getTableState(gameId)?.tileWall?.remainingCount

        assertEquals(remainingAfterFirstDraw, remainingAfterSecondCall, "Second call should not draw another tile.")
    }
}
