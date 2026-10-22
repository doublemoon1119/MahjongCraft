package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortEffects
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AbortGameUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAbortiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAndDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolvePostReactionRoundOutcomeUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolveWinRoundContinuationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToRobbingUseCase
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
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
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
 * 驗證創建房間、加入 AI、開局、完成對局、返回房間及再次開局的完整生命週期。
 * 房間與遊戲用例共用同一個 [AuthoritativeStateStore]，檢查房主、座位、準備狀態與新對局分數。
 *
 * 此測試未固定牌山與座位的隨機來源。固定的 [FakeAiStrategy] 只限制決策方式，
 * 不保證每次產生相同對局；斷言著重狀態轉換，不依賴特定牌序。
 */
class RoomToRoomFullLifecycleIntegrationTest {

    /**
     * 串接房間、遊戲與決策流程的測試依賴。
     *
     * @param historyRecordingEnabled 是否保留權威交易的待寫歷史事件。
     * @property store 房間與對局共用的權威狀態儲存區。
     * @property gameRepo 權威遊戲儲存庫。
     * @property roomRepo 權威房間儲存庫。
     * @property membershipRepo 玩家成員關係儲存庫。
     * @property roomSnapshotRepo 房間快照儲存庫。
     * @property roomEventPublisher 收集房間通知的測試發佈器。
     * @property gameSnapshotRepo 遊戲快照儲存庫。
     * @property gameEventPublisher 收集遊戲通知的測試發佈器。
     * @property presentationPublisher 收集呈現請求的測試發佈器。
     * @property winPresentationHandoff 保存和牌呈現交接資料的元件。
     * @property presentationBusyGate 控制呈現忙碌狀態的測試閘門。
     * @property moduleRegistry 提供內建規則的模組登錄表。
     * @property snapshotSynchronizer 更新觀察者快照的服務。
     * @property handSortPreferenceStore 玩家理牌偏好儲存區。
     * @property createRoomUseCase 建立房間的用例。
     * @property addAiPlayerUseCase 加入 AI 玩家的用例。
     * @property startGameUseCase 將房間轉為對局的用例。
     * @property postActionExhaustiveDrawResolverRegistry 動作完成後的途中流局解析器。
     * @property winSettlementDetailResolverRegistry 和牌詳情解析器。
     * @property extensionCommandExecutor 執行規則擴充命令的元件。
     * @property router 分派玩家命令的路由。
     * @property getLegalActionsUseCase 查詢合法動作的用例。
     * @property aiStrategyRegistry 提供固定決策策略的登錄表。
     * @property aiTurnDriver 建立 AI 決策情境與命令的驅動器。
     * @property clock 決策計時使用的單調時鐘。
     * @property decisionTimerManager 管理玩家決策時間的元件。
     * @property coordinator 推進用例與局間流程的協調器。
     */
    private class Fixtures(historyRecordingEnabled: Boolean = false) {
        val store = AuthoritativeStateStore(historyRecordingEnabled = historyRecordingEnabled, maxPendingHistoryEvents = 10_000)
        val gameRepo = GameRepositoryImpl(store)
        val roomRepo = RoomRepositoryImpl(store)
        val membershipRepo = PlayerMembershipRepositoryImpl()
        val roomSnapshotRepo = FakeRoomSnapshotRepository()
        val roomEventPublisher = FakeRoomEventPublisher()
        val gameSnapshotRepo = FakeGameSnapshotRepository()
        val gameEventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val winPresentationHandoff = WinPresentationHandoff()
        val presentationBusyGate = FakeGamePresentationBusyGate()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, gameSnapshotRepo, GameVisibilityPolicyImpl(moduleRegistry))
        val handSortPreferenceStore = HandSortPreferenceStore()

        val createRoomUseCase = CreateRoomUseCase(store, membershipRepo, roomSnapshotRepo, roomEventPublisher)
        val aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = FakeAiStrategy.KEY).apply {
            register(MahjongAiStrategyDefinition(FakeAiStrategy.KEY) { FakeAiStrategy() })
        }
        val addAiPlayerUseCase = AddAiPlayerUseCase(roomRepo, roomSnapshotRepo, roomEventPublisher, aiStrategyRegistry)
        val startGameUseCase = StartGameUseCase(
            store,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            gameEventPublisher,
            presentationPublisher,
        )

        val postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply {
            BundledRiichiExtension.registerPostActionExhaustiveDrawResolvers(this)
            freeze()
        }
        val winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry().apply {
            BundledRiichiExtension.registerWinSettlementDetailResolvers(this)
            freeze()
        }
        val extensionCommandExecutor = ExtensionGameCommandExecutor(
            registry = ExtensionGameCommandExecutorRegistry().apply {
                BundledRiichiExtension.registerGameCommandHandlers(this)
                freeze()
            },
            context = ExtensionGameCommandContext(
                gameRepository = gameRepo,
                moduleRegistry = moduleRegistry,
                snapshotSynchronizer = snapshotSynchronizer,
                handSortPreferenceStore = handSortPreferenceStore,
                postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,
                eventPublisher = gameEventPublisher,
                presentationPublisher = presentationPublisher,
                declareAndDiscardUseCase = DeclareAndDiscardUseCase(
                    gameRepository = gameRepo,
                    moduleRegistry = moduleRegistry,
                    snapshotSynchronizer = snapshotSynchronizer,
                    handSortPreferenceStore = handSortPreferenceStore,
                    postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,
                    eventPublisher = gameEventPublisher,
                    presentationPublisher = presentationPublisher,
                ),
            ),
        )

        val router = GameActionRouter(
            drawTileUseCase = DrawTileUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher, presentationPublisher),
            discardTileUseCase = DiscardTileUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                postActionExhaustiveDrawResolverRegistry,
                gameEventPublisher,
                presentationPublisher,
            ),
            declareTsumoUseCase = DeclareTsumoUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                gameEventPublisher,
                presentationPublisher,
                winPresentationHandoff,
                winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
                winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
            ),
            declareKanUseCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher, presentationPublisher),
            respondToDiscardUseCase = RespondToDiscardUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                gameEventPublisher,
                presentationPublisher,
                winPresentationHandoff,
                winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
                winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
                postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,
            ),
            respondToRobbingUseCase = RespondToRobbingUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                gameEventPublisher,
                presentationPublisher,
                winPresentationHandoff,
                winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
                winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
            ),
            declareAbortiveDrawUseCase = DeclareAbortiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher),
            extensionCommandExecutor = extensionCommandExecutor,
        )
        val getLegalActionsUseCase = GetLegalActionsUseCase(gameRepo, moduleRegistry)
        val aiTurnDriver = AiTurnDriver(
            gameRepo,
            getLegalActionsUseCase,
            aiStrategyRegistry,
            GameVisibilityPolicyImpl(moduleRegistry),
            moduleRegistry,
            AiDecisionExecutor.direct(failurePolicy = ExtensionFailurePolicy.Default),
            AutomaticGameCommandResolver(moduleRegistry),
        )
        val clock = MonotonicClockImpl()
        val decisionTimerManager = GameDecisionTimerManager(
            gameRepository = gameRepo,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(clock),
            clock = clock,
        )
        val decisionAvailabilityService = GameDecisionAvailabilityService(
            presentationBusyGate,
            decisionTimerManager,
            DecisionTimerSynchronizationService(
                decisionTimerManager,
                gameRepo,
                FakeDecisionTimerUpdatePublisher(),
            ),
        )
        val coordinator = GameFlowCoordinator(
            gameActionRouter = router,
            gameRepository = gameRepo,
            moduleRegistry = moduleRegistry,
            winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
            declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, gameEventPublisher),
            resolvePostReactionRoundOutcomeUseCase = ResolvePostReactionRoundOutcomeUseCase(
                gameRepo,
                moduleRegistry,
                PostReactionRoundOutcomeResolverRegistry().apply { freeze() },
                snapshotSynchronizer,
                winSettlementDetailResolverRegistry,
            ),
            resolveWinRoundContinuationUseCase = ResolveWinRoundContinuationUseCase(
                gameRepo,
                moduleRegistry,
                WinRoundContinuationResolverRegistry().apply { freeze() },
                snapshotSynchronizer,
            ),
            advanceRoundUseCase = AdvanceRoundUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                gameEventPublisher,
                presentationPublisher,
            ),
            returnToRoomUseCase = ReturnToRoomUseCase(
                store,
                roomSnapshotRepo,
                roomEventPublisher,
                presentationPublisher,
            ),
            aiTurnDriver = aiTurnDriver,
            forcedAutoPlayDriver = ForcedAutoPlayDriver(gameRepo, AutomaticGameCommandResolver(moduleRegistry)),
            decisionAvailabilityService = decisionAvailabilityService,
            presentationBusyGate = presentationBusyGate,
            exhaustiveDrawSettlementPresentationService = ExhaustiveDrawSettlementPresentationService(presentationPublisher),
            winPresentationHandoff = winPresentationHandoff,
            presentationPublisher = presentationPublisher,
            abortGameUseCase = AbortGameUseCase(
                store = store,
                gameSnapshots = gameSnapshotRepo,
                roomSnapshots = roomSnapshotRepo,
                roomEvents = roomEventPublisher,
                presentation = presentationPublisher,
                availability = decisionAvailabilityService,
                winHandoff = winPresentationHandoff,
                effects = GameAbortEffects.NONE,
            ),
            automaticCommandResolver = AutomaticGameCommandResolver(moduleRegistry),
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

    /**
     * 執行兩場完整對局並收集各場的待寫歷史事件。
     *
     * @param gameLength 兩場對局使用的賽程長度。
     * @return 依對局完成順序排列的事件清單。
     */
    internal suspend fun measureFullLifecycle(gameLength: RiichiGameLength): List<List<HistoryOutboxEvent>> {
        val measurements = mutableListOf<List<HistoryOutboxEvent>>()
        testFullLifecycle(gameLength) { measurements += it }
        return measurements
    }

    /**
     * 驗證同一房間連續完成兩場對局後仍可恢復房間狀態。
     *
     * @param gameLength 對局使用的賽程長度。
     * @param recordMatch 每場結束後接收待寫歷史事件；null 表示不啟用歷史記錄。
     */
    private suspend fun testFullLifecycle(
        gameLength: RiichiGameLength,
        recordMatch: ((List<HistoryOutboxEvent>) -> Unit)? = null,
    ) {
        val fixtures = Fixtures(historyRecordingEnabled = recordMatch != null)
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
        val firstMatchId = fixtures.store.getGame(gameId)!!.matchId
        assertEquals(roomId, gameId, "The game must reuse the room's own ID.")
        assertNull(fixtures.store.getRoom(roomId), "Room record must be gone once the game starts.")

        // 打完整場東風戰（4 局，含可能的連莊）——房主是真人身分（不是 AI），driveAutomatedPlayers
        // 不會替他行動，這裡用 playFullMatch 額外替房主套用跟 AI 完全相同的固定決策邏輯，交錯呼叫
        // driveAutomatedPlayers（推進 3 個 AI）與房主自己的回合，直到整場對局結束、場地真的退回房間。
        fixtures.playFullMatch(gameId, hostId)
        recordMatch?.invoke(fixtures.store.snapshot().historyRecordingState.pendingEvents.filter { it.matchId == firstMatchId })

        assertNull(fixtures.store.getGame(gameId), "Game record must be removed once the match ends.")
        val roomAfterMatch = fixtures.store.getRoom(roomId)
        assertNotNull(roomAfterMatch, "The venue must hold a Room again once the match ends.")
        assertEquals(hostId, roomAfterMatch.hostId, "Original host must be preserved across the round trip.")
        assertEquals(setOf(hostId) + aiIds, roomAfterMatch.playerIds.toSet(), "All 4 original players must still be seated in the room.")
        assertEquals(aiIds, roomAfterMatch.readyPlayerIds.toSet(), "AI players must stay ready; only the human host isn't auto-readied.")
        assertTrue(roomAfterMatch.canStart, "Room must be immediately startable again (AI already ready, host doesn't need to ready).")

        // 重新開第二場對局，驗證真的能再打一次，不是卡在某個殘留狀態
        val secondStartResult = fixtures.startGameUseCase(roomId, hostId)
        assertTrue(secondStartResult is Outcome.Success, "Starting a second game at the same venue should succeed: $secondStartResult")
        val secondGameId = secondStartResult.value
        assertEquals(roomId, secondGameId)

        val secondGame = fixtures.store.getGame(secondGameId)
        assertNotNull(secondGame)
        val secondMatchId = secondGame.matchId
        assertTrue(secondGame.tableState.players.all { it.score == config.ruleConfig.scoreConfig.initialScore }, "The second match must start with fresh initial scores, not carry over the first match's final scores.")
        assertEquals(1, secondGame.tableState.roundNumber, "The second match must start from round 1 again.")

        fixtures.playFullMatch(secondGameId, hostId)
        recordMatch?.invoke(fixtures.store.snapshot().historyRecordingState.pendingEvents.filter { it.matchId == secondMatchId })

        assertNull(fixtures.store.getGame(secondGameId), "Second match's game record must also be removed once it ends.")
        assertNotNull(fixtures.store.getRoom(roomId), "The venue must hold a Room again after the second match too.")
    }

    /**
     * 交錯呼叫 [GameFlowCoordinator.driveAutomatedPlayers]（推進所有 AI 玩家）與 [driveHostTurn]
     * （替真人房主套用跟 AI 完全相同的固定決策邏輯），直到整場對局結束、場地從 Game 轉回 Room 為止。
     *
     * 房主在 [Fixtures] 這一層是真正的真人身分（不是透過 `AddAiPlayerUseCase` 加入的 AI），
     * `driveAutomatedPlayers` 不會替他行動——這正是端對端測試「真的有一個人類玩家」這件事，不能
     * 跳過。
     *
     * @param gameId 要推進的遊戲識別碼。
     * @param hostId 由測試策略代為操作的真人房主識別碼。
     * @throws IllegalStateException AI 推進後房主無可執行決策，或超過 [MAX_HOST_TURNS] 仍未結束。
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
     * （搶槓反應 → 捨牌反應 → 自己回合）用 [strategy] 決定命令並透過測試用的 [dispatchThenDrive] 送出。
     *
     * @param gameId 要查詢與操作的遊戲識別碼。
     * @param hostId 真人房主識別碼。
     * @param strategy 替房主選擇動作的策略。
     * @return 是否已送出命令；不代表命令執行成功。無決策或遊戲不存在時為 false。
     */
    private suspend fun Fixtures.driveHostTurn(gameId: Uuid, hostId: Uuid, strategy: MahjongAiStrategy): Boolean {
        val game = gameRepo.getGame(gameId) ?: return false
        val state = game.tableState
        val visibilityPolicy = GameVisibilityPolicyImpl(moduleRegistry)

        val pendingRobbingReaction = state.pendingRobbingReaction
        if (pendingRobbingReaction != null && hostId in pendingRobbingReaction.eligiblePlayerIds && hostId !in pendingRobbingReaction.responses) {
            val command = decideFor(gameId, game, hostId, AiDecisionPhase.RespondingToRobbing, strategy, visibilityPolicy)
            coordinator.dispatchThenDrive(gameId, hostId, command)
            return true
        }

        val pendingReaction = state.pendingReaction
        if (pendingReaction != null && hostId in pendingReaction.eligiblePlayerIds && hostId !in pendingReaction.responses) {
            val command = decideFor(gameId, game, hostId, AiDecisionPhase.RespondingToDiscard, strategy, visibilityPolicy)
            coordinator.dispatchThenDrive(gameId, hostId, command)
            return true
        }

        if (pendingRobbingReaction == null && pendingReaction == null && state.currentPlayer.id == hostId) {
            val current = state.currentPlayer
            val command = if (current.hand.lastDrawn == null && !current.justClaimedMeld) {
                GameCommand.Draw
            } else {
                decideFor(gameId, game, hostId, AiDecisionPhase.OwnTurn, strategy, visibilityPolicy)
            }
            coordinator.dispatchThenDrive(gameId, hostId, command)
            return true
        }

        return false
    }

    /**
     * 以指定快照及另外查詢的合法動作建立決策情境，直接呼叫策略。
     *
     * 此測試輔助函式不經 AI 決策執行器，也不提供正式驅動器的逾時與條件式提交保護。
     *
     * @param gameId 查詢合法動作的遊戲識別碼。
     * @param game 用於建立觀察者快照的遊戲。
     * @param playerId 要代為決策的玩家。
     * @param phase 本次決策階段。
     * @param strategy 選擇命令的策略。
     * @param visibilityPolicy 建立玩家可見快照的政策。
     * @return 策略選擇的命令。
     */
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
            discardReactionId = game.tableState.pendingReaction?.id,
        )
        return strategy.decideGameCommand(context)
    }

    /** 測試推進的安全上限。 */
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
        /** 策略註冊用識別碼。 */
        companion object {
            /** 生命週期測試使用的固定決策策略。 */
            const val KEY = "test:deterministic-lifecycle"
        }

        override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
            AiDecisionPhase.RespondingToDiscard ->
                GameCommand.RespondToDiscard(
                    context.legalActions.firstOrNull { it is GameAction.Ron } ?: GameAction.Pass,
                    checkNotNull(context.discardReactionId),
                )

            AiDecisionPhase.RespondingToRobbing ->
                GameCommand.RespondToRobbing(context.legalActions.firstOrNull { it is GameAction.Ron } ?: GameAction.Pass)

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
