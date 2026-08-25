package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
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
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ReturnToRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.StartGameUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.AddAiPlayerUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.CreateRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.time.MonotonicClockImpl
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 端對端整合測試：從創建房間、加入固定邏輯的 AI、開局、打完整場對局、自動退回房間，到重新開下一場
 * 對局——完整走一輪 Room → Game → Room → Game 的生命週期，全程透過真正的 use case 與共用同一個
 * [AuthoritativeStateStore]（比照 `MahjongAutoDrawServiceTest` 的 Fixtures 寫法，而不是像
 * `GameFlowCoordinatorTest`/`FullMatchIntegrationTest` 那樣用互不相通的假物件）。
 *
 * 牌山本身仍是真隨機——`GameInitializer` 目前沒有任何可注入固定牌序的機制，這裡刻意不為了這支測試
 * 去加（純測試用途，暫無實際場景需要）。改用固定的 AI 決策邏輯（[FakeAiStrategy]，摸切、只在自然
 * 自摸/榮和時才拿）搭配不變量斷言（分數守恆、局數跑完、房間正確重建、能再次開局），驗證整條
 * 生命週期撐得住，而不是驗證某一局一定摸到什麼牌。
 */
class RoomToRoomFullLifecycleIntegrationTest {

    private class Fixtures {
        val store = AuthoritativeStateStore()
        val gameRepo = GameRepositoryImpl(store)
        val roomRepo = RoomRepositoryImpl(store)
        val membershipRepo = PlayerMembershipRepositoryImpl()
        val roomSnapshotRepo = FakeRoomSnapshotRepository()
        val roomEventPublisher = FakeRoomEventPublisher()
        val gameSnapshotRepo = FakeGameSnapshotRepository()
        val gameEventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val presentationBusyGate = FakeGamePresentationBusyGate()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, gameSnapshotRepo, GameVisibilityPolicyImpl())
        val handSortPreferenceStore = HandSortPreferenceStore()

        val createRoomUseCase = CreateRoomUseCase(store, membershipRepo, roomSnapshotRepo, roomEventPublisher)
        val addAiPlayerUseCase = AddAiPlayerUseCase(roomRepo, roomSnapshotRepo, roomEventPublisher)
        val startGameUseCase = StartGameUseCase(
            store,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            gameEventPublisher,
            presentationPublisher,
        )

        val declareRiichiUseCase = DeclareRiichiUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, handSortPreferenceStore, gameEventPublisher, presentationPublisher)
        val extensionCommandRegistry = ExtensionGameCommandExecutorRegistry().apply {
            registerRiichiGameCommandHandler(declareRiichiUseCase)
        }

        val router = GameActionRouter(
            drawTileUseCase = DrawTileUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher, presentationPublisher),
            discardTileUseCase = DiscardTileUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                gameEventPublisher,
                presentationPublisher,
            ),
            declareTsumoUseCase = DeclareTsumoUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher, presentationPublisher),
            declareKanUseCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher, presentationPublisher),
            respondToDiscardUseCase = RespondToDiscardUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                gameEventPublisher,
                presentationPublisher,
            ),
            respondToKanUseCase = RespondToKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher, presentationPublisher),
            declareAbortiveDrawUseCase = DeclareAbortiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher),
            extensionCommandRegistry = extensionCommandRegistry,
        )
        val getLegalActionsUseCase = GetLegalActionsUseCase(gameRepo, moduleRegistry)
        val aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = FakeAiStrategy.KEY).apply {
            register(FakeAiStrategy.KEY) { FakeAiStrategy() }
        }
        val aiTurnDriver = AiTurnDriver(gameRepo, getLegalActionsUseCase, aiStrategyRegistry, GameVisibilityPolicyImpl())
        val clock = MonotonicClockImpl()
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
            declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher),
            declareSuukanNagareUseCase = DeclareSuukanNagareUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher),
            advanceRoundUseCase = AdvanceRoundUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                gameEventPublisher,
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
        )
    }

    /**
     * 驗證完整生命週期：創房 → 房主加 3 個固定邏輯 AI → 開局 → 打完整場東風戰（4 局）→ 自動退回房間
     * → 房間狀態正確（房主不變、玩家與 AI 策略保留、AI 維持準備、房主需要重新準備以外可以直接再開局）
     * → 成功開出第二場全新對局。
     */
    @Test
    fun `test full room to game to room to game lifecycle for east-only`() = runTest {
        testFullLifecycle(RiichiGameLength.East)
    }

    /**
     * 跟上面東風戰同一套驗證，改用半莊（東南風，8 局，含可能的連莊會更長）——`isMatchOver` 判斷本身
     * 通用於 `RiichiGameLength` 任何 `totalRounds`（見 `TableState.advanceRound`），這裡只是把「真的
     * 連續打完好幾局、含場風輪轉」這條路徑也走一次自動測試，不是另外寫一套邏輯。
     */
    @Test
    fun `test full room to game to room to game lifecycle for two-winds`() = runTest {
        testFullLifecycle(RiichiGameLength.TwoWinds)
    }

    private suspend fun testFullLifecycle(gameLength: RiichiGameLength) {
        val fixtures = Fixtures()
        val hostId = Uuid.random()
        val roomId = Uuid.random()
        val config = GameConfig(ruleConfig = RiichiRuleConfig(gameLength = gameLength))

        val createResult = fixtures.createRoomUseCase(roomId, hostId, config)
        assertTrue(createResult is Outcome.Success, "Room creation should succeed: $createResult")

        val aiIds = (1..3).map { index ->
            val addResult = fixtures.addAiPlayerUseCase(roomId, hostId, FakeAiStrategy.KEY)
            assertTrue(addResult is Outcome.Success, "Adding AI #$index should succeed: $addResult")
            addResult.value.aiId
        }.toSet()

        val room = fixtures.roomRepo.getRoom(roomId)
        assertNotNull(room)
        assertTrue(room.canStart, "Room should be startable once the host has 3 ready AI players.")

        val startResult = fixtures.startGameUseCase(roomId, hostId)
        assertTrue(startResult is Outcome.Success, "Starting the game should succeed: $startResult")
        val gameId = startResult.value
        assertEquals(roomId, gameId, "The game must reuse the room's own ID.")
        assertNull(fixtures.store.getRoom(roomId), "Room record must be gone once the game starts.")

        // 打完整場東風戰（4 局，含可能的連莊）——房主是真人身分（不是 AI），driveAutomatedPlayers
        // 不會替他行動，這裡用 playFullMatch 額外替房主套用跟 AI 完全相同的固定決策邏輯，交錯呼叫
        // driveAutomatedPlayers（推進 3 個 AI）與房主自己的回合，直到整場對局結束、桌子真的退回房間。
        fixtures.playFullMatch(gameId, hostId)

        assertNull(fixtures.store.getGame(gameId), "Game record must be removed once the match ends.")
        val roomAfterMatch = fixtures.store.getRoom(roomId)
        assertNotNull(roomAfterMatch, "Table must become a Room again once the match ends.")
        assertEquals(hostId, roomAfterMatch.hostId, "Original host must be preserved across the round trip.")
        assertEquals(setOf(hostId) + aiIds, roomAfterMatch.playerIds.toSet(), "All 4 original players must still be seated in the room.")
        assertEquals(aiIds, roomAfterMatch.readyPlayerIds.toSet(), "AI players must stay ready; only the human host isn't auto-readied.")
        assertTrue(roomAfterMatch.canStart, "Room must be immediately startable again (AI already ready, host doesn't need to ready).")

        // 重新開第二場對局，驗證真的能再打一次，不是卡在某個殘留狀態
        val secondStartResult = fixtures.startGameUseCase(roomId, hostId)
        assertTrue(secondStartResult is Outcome.Success, "Starting a second game at the same table should succeed: $secondStartResult")
        val secondGameId = secondStartResult.value
        assertEquals(roomId, secondGameId)

        val secondGame = fixtures.store.getGame(secondGameId)
        assertNotNull(secondGame)
        assertTrue(secondGame.tableState.players.all { it.score == config.ruleConfig.scoreConfig.initialScore }, "The second match must start with fresh initial scores, not carry over the first match's final scores.")
        assertEquals(1, secondGame.tableState.roundNumber, "The second match must start from round 1 again.")

        fixtures.playFullMatch(secondGameId, hostId)

        assertNull(fixtures.store.getGame(secondGameId), "Second match's game record must also be removed once it ends.")
        assertNotNull(fixtures.store.getRoom(roomId), "Table must become a Room again after the second match too.")
    }

    /**
     * 交錯呼叫 [GameFlowCoordinator.driveAutomatedPlayers]（推進所有 AI 玩家）與 [driveHostTurn]
     * （替真人房主套用跟 AI 完全相同的固定決策邏輯），直到整場對局結束、桌子從 Game 轉回 Room 為止。
     *
     * 房主在 [Fixtures] 這一層是真正的真人身分（不是透過 `AddAiPlayerUseCase` 加入的 AI），
     * `driveAutomatedPlayers` 不會替他行動——這正是端對端測試「真的有一個人類玩家」這件事，不能
     * 跳過。
     *
     * @throws IllegalStateException 跑滿 [MAX_HOST_TURNS] 輪後，AI 與房主都沒有任何動作可做，
     *   但對局依然沒有結束——代表流程真的卡住了。
     */
    private suspend fun Fixtures.playFullMatch(gameId: Uuid, hostId: Uuid) {
        val strategy = FakeAiStrategy()
        repeat(MAX_HOST_TURNS) {
            coordinator.driveAutomatedPlayers(gameId)
            if (gameRepo.getGame(gameId) == null) return // 已經退回房間，對局結束
            if (!driveHostTurn(gameId, hostId, strategy)) {
                error("playFullMatch: neither the AI players nor the host had anything to do for game $gameId, but the match hasn't ended.")
            }
        }
        error("playFullMatch did not converge for game $gameId after $MAX_HOST_TURNS host turns.")
    }

    /**
     * 替 [hostId] 解出目前是否有反應／自己回合的決策要做，有的話比照 [AiTurnDriver] 的判斷順序
     * （搶槓反應 → 捨牌反應 → 自己回合）用 [strategy] 決定命令並透過 [GameFlowCoordinator.invoke]
     * 送出。
     *
     * @return 是否真的替房主送出了一個命令；沒有任何決策要做時回傳 false。
     */
    private suspend fun Fixtures.driveHostTurn(gameId: Uuid, hostId: Uuid, strategy: MahjongAiStrategy): Boolean {
        val game = gameRepo.getGame(gameId) ?: return false
        val state = game.tableState
        val visibilityPolicy = GameVisibilityPolicyImpl()

        val pendingKanReaction = state.pendingKanReaction
        if (pendingKanReaction != null && hostId in pendingKanReaction.eligiblePlayerIds && hostId !in pendingKanReaction.responses) {
            val command = decideFor(gameId, game, hostId, AiDecisionPhase.RespondingToKan, strategy, visibilityPolicy)
            coordinator(gameId, hostId, command)
            return true
        }

        val pendingReaction = state.pendingReaction
        if (pendingReaction != null && hostId in pendingReaction.eligiblePlayerIds && hostId !in pendingReaction.responses) {
            val command = decideFor(gameId, game, hostId, AiDecisionPhase.RespondingToDiscard, strategy, visibilityPolicy)
            coordinator(gameId, hostId, command)
            return true
        }

        if (pendingKanReaction == null && pendingReaction == null && state.currentPlayer.id == hostId) {
            val current = state.currentPlayer
            val command = if (current.hand.lastDrawn == null && !current.justClaimedMeld) {
                GameCommand.Draw
            } else {
                decideFor(gameId, game, hostId, AiDecisionPhase.OwnTurn, strategy, visibilityPolicy)
            }
            coordinator(gameId, hostId, command)
            return true
        }

        return false
    }

    /** 組出 [AiDecisionContext] 並問 [strategy] 該怎麼行動，邏輯與 [AiTurnDriver.decide] 完全對應。 */
    private suspend fun Fixtures.decideFor(
        gameId: Uuid,
        game: Game,
        playerId: Uuid,
        phase: AiDecisionPhase,
        strategy: MahjongAiStrategy,
        visibilityPolicy: GameVisibilityPolicyImpl,
    ): GameCommand {
        val legalActionsResult = getLegalActionsUseCase(gameId, playerId)
        val legalActions = (legalActionsResult as? Outcome.Success)?.value ?: emptyList()
        val context = AiDecisionContext(
            snapshot = visibilityPolicy.snapshotFor(game, playerId),
            selfId = playerId,
            phase = phase,
            legalActions = legalActions,
        )
        return strategy.decide(context)
    }

    private companion object {
        /** [playFullMatch] 的最大輪次上限，避免測試真的卡住時無限迴圈。 */
        const val MAX_HOST_TURNS = 2000
    }

    /**
     * 供整場對局整合測試使用的固定策略：刻意不主動鳴牌（吃/碰/槓/立直/九種九牌），只在自然出現
     * 榮和/自摸機會時才拿，其餘時候單純摸牌後打出剛摸到的牌——比照 `FullMatchIntegrationTest` 的
     * 既有寫法，這裡關心的是完整生命週期撐不撐得住，不是驗證每種行牌路徑。
     */
    private class FakeAiStrategy : MahjongAiStrategy {
        companion object {
            const val KEY = "fake-deterministic-lifecycle"
        }

        override suspend fun decide(context: AiDecisionContext): GameCommand = when (context.phase) {
            AiDecisionPhase.RespondingToDiscard ->
                GameCommand.RespondToDiscard(context.legalActions.firstOrNull { it is GameAction.Ron } ?: GameAction.Pass)

            AiDecisionPhase.RespondingToKan ->
                GameCommand.RespondToKan(context.legalActions.firstOrNull { it is GameAction.Ron } ?: GameAction.Pass)

            AiDecisionPhase.OwnTurn -> {
                if (context.legalActions.contains(GameAction.Tsumo)) {
                    GameCommand.Tsumo
                } else {
                    val hand = context.snapshot.players.first { it.id == context.selfId }.hand
                    val tileId = hand.lastDrawn?.id ?: hand.standingTiles.first().id
                    GameCommand.Discard(tileId)
                }
            }
        }
    }
}
