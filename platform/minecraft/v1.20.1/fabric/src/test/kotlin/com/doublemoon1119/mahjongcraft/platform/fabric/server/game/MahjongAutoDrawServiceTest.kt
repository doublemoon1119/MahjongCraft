package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiTurnDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ForcedAutoPlayDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameActionRouter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.registerRiichiGameCommandHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAbortiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareSuukanNagareUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolvePostReactionRoundOutcomeUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolveWinRoundContinuationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ReturnToRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
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
        val roomSnapshotRepo = FakeRoomSnapshotRepository()
        val roomEventPublisher = FakeRoomEventPublisher()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val handSortPreferenceStore = HandSortPreferenceStore()
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val winPresentationHandoff = WinPresentationHandoff()
        val presentationBusyGate = FakeGamePresentationBusyGate()
        val declareRiichiUseCase = DeclareRiichiUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, handSortPreferenceStore, eventPublisher, presentationPublisher)
        val extensionCommandRegistry = ExtensionGameCommandExecutorRegistry().apply {
            registerRiichiGameCommandHandler(declareRiichiUseCase)
        }
        val router = GameActionRouter(
            drawTileUseCase = DrawTileUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
            discardTileUseCase = DiscardTileUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                eventPublisher,
                presentationPublisher,
            ),
            declareTsumoUseCase = DeclareTsumoUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher, winPresentationHandoff),
            declareKanUseCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
            respondToDiscardUseCase = RespondToDiscardUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                eventPublisher,
                presentationPublisher,
                winPresentationHandoff,
            ),
            respondToKanUseCase = RespondToKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher, winPresentationHandoff),
            declareAbortiveDrawUseCase = DeclareAbortiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            extensionCommandRegistry = extensionCommandRegistry,
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
            extensionCommandRegistry = extensionCommandRegistry,
            gameRepository = gameRepo,
            moduleRegistry = moduleRegistry,
            declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            resolvePostReactionRoundOutcomeUseCase = ResolvePostReactionRoundOutcomeUseCase(
                gameRepo,
                moduleRegistry,
                PostReactionRoundOutcomeResolverRegistry().apply { freeze() },
                snapshotSynchronizer,
            ),
            resolveWinRoundContinuationUseCase = ResolveWinRoundContinuationUseCase(
                gameRepo,
                moduleRegistry,
                WinRoundContinuationResolverRegistry().apply { freeze() },
                snapshotSynchronizer,
            ),
            declareSuukanNagareUseCase = DeclareSuukanNagareUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            advanceRoundUseCase = AdvanceRoundUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                eventPublisher,
                presentationPublisher,
            ),
            returnToRoomUseCase = ReturnToRoomUseCase(store, roomSnapshotRepo, roomEventPublisher, presentationPublisher),
            aiTurnDriver = aiTurnDriver,
            forcedAutoPlayDriver = ForcedAutoPlayDriver(gameRepo),
            decisionTimerManager = decisionTimerManager,
            decisionTimerSynchronizationService = DecisionTimerSynchronizationService(
                decisionTimerManager,
                gameRepo,
                FakeDecisionTimerUpdatePublisher(),
            ),
            presentationBusyGate = presentationBusyGate,
            exhaustiveDrawSettlementPresentationService = ExhaustiveDrawSettlementPresentationService(presentationPublisher),
            winPresentationHandoff = winPresentationHandoff,
            presentationPublisher = presentationPublisher,
        )
        val feedbackPublisher = FakeMinecraftPlayerFeedbackPublisher()
        val membershipRepository = PlayerMembershipRepositoryImpl()
        val candidateResolver = GameActionCandidateResolver(gameRepo, membershipRepository, getLegalActionsUseCase, moduleRegistry)
        val autoDrawService = MahjongAutoDrawService(
            gameRepo,
            coordinator,
            feedbackPublisher,
            TablePresentationBusyTracker(FabricServerHolder(), TableLocationRegistry()),
            candidateResolver,
        )
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
     * 驗證真人莊家尚未摸牌時，`checkAndAutoDraw` 會實際幫他摸到一張牌，並依序發布
     * [MinecraftPlayerFeedback.YourTurn] 通知他輪到自己了、緊接著發布
     * [MinecraftPlayerFeedback.ShowHand] 讓他不需要另外手動查詢 `/mahjongcraft game hand`
     * 就能看到目前的手牌與合法動作。
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

        assertEquals(2, fixtures.feedbackPublisher.published.size)
        val (notifiedPlayerId, yourTurnFeedback) = fixtures.feedbackPublisher.published[0]
        assertEquals(state.currentPlayer.id, notifiedPlayerId)
        val yourTurn = assertIs<MinecraftPlayerFeedback.YourTurn>(yourTurnFeedback)
        assertEquals(drawnTile.tile, yourTurn.drawnTile)

        val (showHandPlayerId, showHandFeedback) = fixtures.feedbackPublisher.published[1]
        assertEquals(state.currentPlayer.id, showHandPlayerId)
        val showHand = assertIs<MinecraftPlayerFeedback.ShowHand>(showHandFeedback)
        assertEquals(state.currentPlayer.hand.standingTiles.map { it.tile }, showHand.standingTiles)
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

    /**
     * 迴歸測試：驗證對局結束後先停在可持久化的終局呈現邊界，之後桌子真的從 Game
     * 轉回 Room（不是只標記 [Game.isMatchOver]）——
     * 兩者共用同一個 [AuthoritativeStateStore]，用來驗證 `ReturnToRoomUseCase` 真的接得到
     * `GameFlowCoordinator` 銜接的呼叫，而不是像 `GameFlowCoordinatorTest` 那樣用互不相通的
     * 假物件（那邊只驗證編排時機，不驗證轉移本身）。
     *
     * 四位玩家都設成 AI（含房主本人）——這裡只驗證 `Game.hostId` 能不能正確往返、Room 能不能正確
     * 重建，不需要真人玩家；若房主是唯一的真人玩家，`GameInitializer.initialize` 內部洗牌座位是
     * 隨機的，房主可能剛好被排到莊家（東家）座位，`driveAutomatedPlayers` 不會替真人行動，測試會在
     * 空牌山的第一次摸牌前卡住、變成間歇性失敗——曾經在這裡踩過一次。
     */
    @Test
    fun `test match ending moves the table from Game back to Room`() = runTest {
        val fixtures = Fixtures()
        val playerIds = List(4) { Uuid.random() }
        val hostId = playerIds.first()
        val gameId = Uuid.random()
        val aiStrategyKeys = playerIds.associateWith { RandomAiStrategy.KEY }
        val module = fixtures.moduleRegistry.getModule(RiichiRuleConfig(gameLength = RiichiGameLength.OneGame))
        val initializationResult = GameInitializer.initialize(
            id = gameId,
            playerIds = playerIds,
            module = module,
            aiPlayerStrategyKeys = aiStrategyKeys,
        )
        val emptyWallState = initializationResult.tableState.copy(tileWall = TileWall(emptyList()))
        fixtures.gameRepo.setTableState(emptyWallState)
        // GameInitializer 依座位風重排玩家順序，setTableState 預設把 hostId 定成第一位玩家的 id，
        // 不保證真的是 hostId 這位玩家——比照 StartGameUseCase 實際上是明確從 Room.hostId 帶入，
        // 這裡也要明確蓋回去才是在測「hostId 有沒有被正確保留」，不是在測隨機湊巧對上。
        fixtures.gameRepo.updateGame(gameId) { game -> game!!.copy(hostId = hostId) to Unit }

        fixtures.coordinator.driveAutomatedPlayers(gameId)

        val settledGame = fixtures.store.getGame(gameId)
        assertNotNull(settledGame, "Game must remain available while the match settlement presentation is pending.")
        assertTrue(settledGame.isMatchOver)
        assertEquals(PendingGameTransition.ReturnToRoom, settledGame.pendingTransition)
        assertTrue(fixtures.coordinator.resumePendingGameTransition(gameId))

        assertNull(fixtures.store.getGame(gameId), "Game record must be removed once the match ends.")
        val room = fixtures.store.getRoom(gameId)
        assertNotNull(room, "Table must become a Room again once the match ends.")
        assertEquals(hostId, room.hostId, "Original room host must be preserved across the round trip.")
        assertEquals(playerIds.toSet(), room.playerIds.toSet())
        assertEquals(aiStrategyKeys, room.aiPlayerStrategyKeys)
        assertEquals(playerIds.toSet(), room.readyPlayerIds.toSet(), "All-AI table means everyone (including the AI host) stays ready.")
    }
}
