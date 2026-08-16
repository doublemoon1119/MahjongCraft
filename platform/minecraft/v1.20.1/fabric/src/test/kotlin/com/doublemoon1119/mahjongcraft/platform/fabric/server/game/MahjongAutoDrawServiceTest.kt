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
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        val feedbackPublisher = FakeMinecraftPlayerFeedbackPublisher()
        val autoDrawService = MahjongAutoDrawService(gameRepo, coordinator, feedbackPublisher)
    }

    /** 提供固定時間的假時鐘，避免測試依賴真實系統時間。 */
    private class MutableMonotonicClock : MonotonicClock {
        override fun nowMillis(): Long = 0L
    }

    /** 記錄每次 [publish] 呼叫，供測試驗證 [MahjongAutoDrawService] 是否正確發布輪到自己的通知。 */
    private class FakeMinecraftPlayerFeedbackPublisher : MinecraftPlayerFeedbackPublisher {
        val published = mutableListOf<Pair<Uuid, MinecraftPlayerFeedback>>()

        override fun publish(playerId: Uuid, feedback: MinecraftPlayerFeedback) {
            published += playerId to feedback
        }
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

    /**
     * 驗證真人莊家尚未摸牌時，`checkAndAutoDraw` 會實際幫他摸到一張牌，並發布
     * [MinecraftPlayerFeedback.YourTurn] 通知他輪到自己了。
     */
    @Test
    fun `test checkAndAutoDraw draws for human player who has not drawn yet`() = runTest {
        val fixtures = Fixtures()
        val gameId = fixtures.createStartedGame()

        fixtures.autoDrawService.checkAndAutoDraw(gameId)

        val state = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(state)
        val drawnTile = state.currentPlayer.hand.lastDrawn
        assertNotNull(drawnTile, "Human dealer should have been auto-drawn a tile.")

        val (notifiedPlayerId, feedback) = fixtures.feedbackPublisher.published.single()
        assertEquals(state.currentPlayer.id, notifiedPlayerId)
        val yourTurn = assertIs<MinecraftPlayerFeedback.YourTurn>(feedback)
        assertEquals(drawnTile.tile, yourTurn.drawnTile)
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

    /**
     * 驗證目前玩家是已進入強制自動操作的真人時，`checkAndAutoDraw` 不會介入（改由
     * `ForcedAutoPlayDriver` 透過 `driveAutomatedPlayers` 代打）。
     *
     * 迴歸測試：這個排除條件原本沒有，`checkAndAutoDraw` 會嘗試幫這種玩家摸牌，被
     * [GameFlowCoordinator] 的強制自動操作守門檢查擋下、靜默失敗。
     */
    @Test
    fun `test checkAndAutoDraw skips when current player is forced auto play`() = runTest {
        val fixtures = Fixtures()
        val gameId = fixtures.createStartedGame()
        val dealerId = fixtures.gameRepo.getTableState(gameId)!!.currentPlayer.id
        fixtures.gameRepo.updateGame(gameId) { game ->
            game!!.copy(forcedAutoPlayPlayerIds = setOf(dealerId)) to Unit
        }

        fixtures.autoDrawService.checkAndAutoDraw(gameId)

        val state = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(state)
        assertNull(state.currentPlayer.hand.lastDrawn, "Forced auto-play player should not be auto-drawn by MahjongAutoDrawService.")
    }

    /**
     * 迴歸測試：驗證玩家進入強制自動操作後，即使沒有任何逾時事件（沒有計時器可等），單純重複呼叫
     * [GameFlowCoordinator.driveAutomatedPlayers] 仍然能幫他完成當下卡住的那次捨牌，不會卡住。
     *
     * 對應 `FabricDecisionTimerScheduler` 現在每個 tick 都巡邏所有進行中對局呼叫
     * `driveAutomatedPlayers` 的設計——這裡驗證的正是這個心跳背後依賴的核心行為：強制自動操作的
     * 玩家不需要逾時事件也能被 `driveAutomatedPlayers` 正確推進。
     *
     * 摸牌先透過 [MahjongAutoDrawService] 走一般玩家的機械摸牌路徑，比照真實流程——強制自動操作只會
     * 在玩家已經摸過牌、卡在「該打哪張」這個決策上逾時才成立（見 [GameDecisionAuthorityResolver]，
     * `OWN_TURN` 一定要求 `lastDrawn != null`），不會發生在「連牌都還沒摸」的狀態。
     */
    @Test
    fun `test driveAutomatedPlayers advances a forced auto play player without any timeout event`() = runTest {
        val fixtures = Fixtures()
        val gameId = fixtures.createStartedGame()
        val dealerId = fixtures.gameRepo.getTableState(gameId)!!.currentPlayer.id
        fixtures.autoDrawService.checkAndAutoDraw(gameId)
        fixtures.gameRepo.updateGame(gameId) { game ->
            game!!.copy(forcedAutoPlayPlayerIds = setOf(dealerId)) to Unit
        }

        fixtures.coordinator.driveAutomatedPlayers(gameId)

        val game = fixtures.gameRepo.getGame(gameId)
        assertNotNull(game)
        assertEquals(1, game.tableState.players.first { it.id == dealerId }.discardPile.entries.size, "Forced player should have discarded the tile it was stuck on.")
        assertTrue(dealerId !in game.forcedAutoPlayPlayerIds, "Player must regain control after the single missed decision is served.")
    }
}
