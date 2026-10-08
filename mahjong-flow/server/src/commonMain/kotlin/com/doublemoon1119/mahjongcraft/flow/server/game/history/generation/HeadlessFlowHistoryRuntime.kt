package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdate
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiTurnDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ForcedAutoPlayDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameActionRouter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAbortiveDrawUseCase
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
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/**
 * 不依賴平台或測試 fixture 的單場真實 Flow 執行環境。
 *
 * 用來以真實流程驗證歷史記錄與封存管線。規則整合一律取自執行環境已完成登記的 [HeadlessHistoryRegistries]，
 * 與正式對局使用同一套登記；權威狀態、快照與事件則全部隔離，不影響正式玩家。
 *
 * @property scenario 生成情境。
 * @property store 隔離權威狀態儲存。
 * @property venueId 場地識別碼。
 * @property gameRepository 隔離對局 repository。
 * @property coordinator 真實流程 coordinator。
 * @property gameId 目前對局識別碼。
 * @property match 場次識別碼。
 */
class HeadlessFlowHistoryRuntime private constructor(
    override val scenario: HeadlessHistoryScenario,
    override val store: AuthoritativeStateStore,
    override val venueId: Uuid,
    private val gameRepository: GameRepositoryImpl,
    private val coordinator: GameFlowCoordinator,
    private val gameId: Uuid,
    private val match: Uuid,
) : HeadlessHistoryMatchRuntime {
    /**
     * 目前場次識別碼。
     *
     * @return 場次 UUID。
     */
    override suspend fun matchId(): Uuid = match

    /**
     * 推進一個自動玩家步驟。
     *
     * @return 是否實際推進權威流程。
     */
    override suspend fun step(): Boolean = coordinator.advanceAutomatedPlayerStep(gameId)

    /**
     * 取得目前對局；返回房間後為 null。
     *
     * @return 目前對局，或已返回房間時的 null。
     */
    override suspend fun currentGame(): Game? = gameRepository.getGame(gameId)

    /** 建立隔離且完整的全 AI 對局執行環境。 */
    companion object {
        /**
         * 建立一場使用真實 AI 策略的無頭對局。
         *
         * @param scenario 對局規則與場長情境。
         * @param registries 執行環境已完成登記的規則整合。
         * @param store 承載這場對局的權威來源；多場對局可共用同一個來源，如同正式伺服器上同時進行的多桌。
         * @param stepTimer 累計 AI 決策與快照同步耗時的計時器；null 時不量測。歷史記錄耗時由 [store] 自己的觀察者量測。
         * @return 已開局且可逐步推進的 runtime。
         */
        suspend fun create(
            scenario: HeadlessHistoryScenario,
            registries: HeadlessHistoryRegistries,
            store: AuthoritativeStateStore = AuthoritativeStateStore(historyRecordingEnabled = true),
            stepTimer: HeadlessStepTimer? = null,
        ): HeadlessFlowHistoryRuntime {
            val gameRepository = GameRepositoryImpl(store)
            val roomRepository = RoomRepositoryImpl(store)
            val membership = PlayerMembershipRepositoryImpl()
            val rooms = MemoryRoomSnapshots()
            val gameSnapshots = MemoryGameSnapshots()
            val roomEvents = NoOpRoomEvents()
            val gameEvents = NoOpGameEvents()
            val presentation = NoOpPresentation()
            val busy = NoOpBusyGate()
            val moduleRegistry = registries.moduleRegistry
            val snapshotPolicy = GameVisibilityPolicyImpl(moduleRegistry).let { policy ->
                if (stepTimer == null) policy else TimedVisibilityPolicy(policy, stepTimer::addSnapshotSync)
            }
            val aiPolicy = GameVisibilityPolicyImpl(moduleRegistry).let { policy ->
                if (stepTimer == null) policy else TimedVisibilityPolicy(policy, stepTimer::addAiDecision)
            }
            val aiStrategies = registries.aiStrategyRegistry.let { strategies ->
                if (stepTimer == null) strategies else TimedAiStrategyRegistry(strategies, stepTimer)
            }
            val synchronizer = GameSnapshotSynchronizer(gameRepository, gameSnapshots, snapshotPolicy)
            val handSort = HandSortPreferenceStore()
            val create = CreateRoomUseCase(store, membership, rooms, roomEvents)
            val addAi = AddAiPlayerUseCase(roomRepository, rooms, roomEvents)
            val createCue = registries.winCelebrationCueResolverRegistry
            val winHandoff = WinPresentationHandoff()
            val postAction = registries.postActionExhaustiveDrawResolverRegistry
            val winDetails = registries.winSettlementDetailResolverRegistry
            val commands = ExtensionGameCommandExecutor(
                registry = registries.gameCommandRegistry,
                context = ExtensionGameCommandContext(
                    gameRepository = gameRepository,
                    moduleRegistry = moduleRegistry,
                    snapshotSynchronizer = synchronizer,
                    handSortPreferenceStore = handSort,
                    postActionExhaustiveDrawResolverRegistry = postAction,
                    eventPublisher = gameEvents,
                    presentationPublisher = presentation,
                ),
            )
            val router = GameActionRouter(
                DrawTileUseCase(gameRepository, moduleRegistry, synchronizer, gameEvents, presentation),
                DiscardTileUseCase(gameRepository, moduleRegistry, synchronizer, handSort, postAction, gameEvents, presentation),
                DeclareTsumoUseCase(gameRepository, moduleRegistry, synchronizer, gameEvents, presentation, winHandoff, createCue, winDetails),
                DeclareKanUseCase(gameRepository, moduleRegistry, synchronizer, gameEvents, presentation),
                RespondToDiscardUseCase(gameRepository, moduleRegistry, synchronizer, handSort, gameEvents, presentation, winHandoff, createCue, winDetails, postAction),
                RespondToRobbingUseCase(gameRepository, moduleRegistry, synchronizer, gameEvents, presentation, winHandoff, createCue, winDetails),
                DeclareAbortiveDrawUseCase(gameRepository, moduleRegistry, synchronizer, gameEvents),
                commands,
            )
            val getLegal = GetLegalActionsUseCase(gameRepository, moduleRegistry)
            val ai = AiTurnDriver(gameRepository, getLegal, aiStrategies, aiPolicy, moduleRegistry)
            val clock = MonotonicClockImpl()
            val timers = GameDecisionTimerManager(gameRepository, GameDecisionAuthorityResolver(), PlayerDecisionTimerFactory(clock), clock)
            val timerSync = DecisionTimerSynchronizationService(timers, gameRepository, NoOpTimerUpdates())
            val coordinator = GameFlowCoordinator(
                gameActionRouter = router,
                gameRepository = gameRepository,
                moduleRegistry = moduleRegistry,
                winSettlementDetailResolverRegistry = winDetails,
                declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(gameRepository, moduleRegistry, synchronizer, gameEvents),
                resolvePostReactionRoundOutcomeUseCase = ResolvePostReactionRoundOutcomeUseCase(
                    gameRepository,
                    moduleRegistry,
                    registries.postReactionRoundOutcomeResolverRegistry,
                    synchronizer,
                    winDetails,
                ),
                resolveWinRoundContinuationUseCase = ResolveWinRoundContinuationUseCase(
                    gameRepository,
                    moduleRegistry,
                    registries.winRoundContinuationResolverRegistry,
                    synchronizer,
                ),
                advanceRoundUseCase = AdvanceRoundUseCase(gameRepository, moduleRegistry, synchronizer, handSort, gameEvents, presentation),
                returnToRoomUseCase = ReturnToRoomUseCase(store, rooms, roomEvents, presentation),
                aiTurnDriver = ai,
                forcedAutoPlayDriver = ForcedAutoPlayDriver(gameRepository),
                automaticDecisionDriver = null,
                decisionAvailabilityService = GameDecisionAvailabilityService(busy, timers, timerSync),
                exhaustiveDrawSettlementPresentationService = ExhaustiveDrawSettlementPresentationService(presentation),
                winPresentationHandoff = winHandoff,
                presentationPublisher = presentation,
                presentationBusyGate = busy,
            )
            val start = StartGameUseCase(store, moduleRegistry, synchronizer, handSort, gameEvents, presentation)
            val venueId = Uuid.random()
            val host = Uuid.random()
            val config = GameConfig(ruleConfig = scenario.ruleConfig)
            check(create(venueId, host, config, hostAiStrategyKey = BuiltInAiStrategyKeys.BEGINNER) is Outcome.Success)
            repeat(scenario.ruleConfig.maxPlayers - 1) { check(addAi(venueId, host, BuiltInAiStrategyKeys.BEGINNER) is Outcome.Success) }
            val started = start(venueId, host)
            check(started is Outcome.Success) { "Headless history runtime failed to start game: $started" }
            val id = started.value
            val game = checkNotNull(store.getGame(id))
            return HeadlessFlowHistoryRuntime(scenario, store, venueId, gameRepository, coordinator, id, game.matchId)
        }
    }
}

/** 隔離房間快照儲存。 */
private class MemoryRoomSnapshots : RoomSnapshotRepository {
    /** 觀察者與房間快照的隔離索引。 */
    private val data = mutableMapOf<Pair<Uuid, Uuid>, RoomSnapshot>()

    /** 讀取觀察者房間快照。 */
    override suspend fun getSnapshot(roomId: Uuid, observerId: Uuid): RoomSnapshot? = data[roomId to observerId]

    /** 寫入觀察者房間快照。 */
    override suspend fun setSnapshot(observerId: Uuid, snapshot: RoomSnapshot) {
        data[snapshot.id to observerId] = snapshot
    }

    /** 移除觀察者房間快照。 */
    override suspend fun removeSnapshot(roomId: Uuid, observerId: Uuid) {
        data.remove(roomId to observerId)
    }

    /** 列出房間觀察者。 */
    override suspend fun getAllObservers(roomId: Uuid): Set<Uuid> = data.keys.filter { it.first == roomId }.map { it.second }.toSet()

    /** 清空隔離快照。 */
    override suspend fun clearAll() {
        data.clear()
    }
}

/** 隔離對局快照儲存。 */
private class MemoryGameSnapshots : GameSnapshotRepository {
    /** 觀察者與對局快照的隔離索引。 */
    private val data = mutableMapOf<Pair<Uuid, Uuid>, TableStateSnapshot>()

    /** 讀取觀察者對局快照。 */
    override suspend fun getSnapshot(gameId: Uuid, observerId: Uuid) = data[gameId to observerId]

    /** 寫入觀察者對局快照。 */
    override suspend fun setSnapshot(observerId: Uuid, snapshot: TableStateSnapshot) {
        data[snapshot.id to observerId] = snapshot
    }

    /** 移除觀察者對局快照。 */
    override suspend fun removeSnapshot(gameId: Uuid, observerId: Uuid) {
        data.remove(gameId to observerId)
    }

    /** 列出對局觀察者。 */
    override suspend fun getAllObservers(gameId: Uuid) = data.keys.filter { it.first == gameId }.map { it.second }.toSet()

    /** 清空隔離快照。 */
    override suspend fun clearAll() {
        data.clear()
    }
}

/** 丟棄房間通知的無頭 adapter。 */
private class NoOpRoomEvents : RoomEventPublisher {
    /** 丟棄加入通知。 */
    override suspend fun publishJoin(roomId: Uuid, targetPlayerId: Uuid, joinedPlayerId: Uuid, reason: JoinReason) = Unit

    /** 丟棄離開通知。 */
    override suspend fun publishLeave(roomId: Uuid, targetPlayerId: Uuid, leftPlayerId: Uuid, reason: LeaveReason) = Unit

    /** 丟棄準備通知。 */
    override suspend fun publishReady(roomId: Uuid, targetPlayerId: Uuid, readyPlayerId: Uuid, isReady: Boolean) = Unit

    /** 丟棄設定通知。 */
    override suspend fun publishConfigChanged(roomId: Uuid, targetPlayerId: Uuid, newConfig: MahjongRuleConfig) = Unit
}

/** 丟棄對局通知的無頭 adapter。 */
private class NoOpGameEvents : GameEventPublisher {
    /** 丟棄單一玩家事件。 */
    override suspend fun publish(gameId: Uuid, targetPlayerId: Uuid, actorId: Uuid, action: GameAction) = Unit

    /** 丟棄廣播給所有觀察者的事件。 */
    override suspend fun publishToAllObservers(gameId: Uuid, seatedPlayerIds: Collection<Uuid>, actorId: Uuid, action: GameAction) = Unit
}

/** 完成呈現握手但不產生平台副作用的無頭 adapter。 */
private class NoOpPresentation : GamePresentationPublisher {
    /** 丟棄流局結算呈現。 */
    override fun publishExhaustiveDrawSettlement(gameId: Uuid, request: ExhaustiveDrawSettlementPresentationRequest) = Unit

    /** 丟棄終局結算呈現。 */
    override fun publishMatchSettlement(gameId: Uuid, request: MatchSettlementPresentationRequest) = Unit

    /** 丟棄骰子呈現。 */
    override fun publishDiceRoll(gameId: Uuid, dice: DiceRollResult, dealerSeatIndex: Int, roundNumber: Int, comboCount: Int) = Unit

    /** 丟棄牌牆呈現。 */
    override fun publishWallStructure(gameId: Uuid, assemblyStructure: Map<Uuid, TileWallPosition>, layout: TileWallPhysicalLayout, dealerSeatIndex: Int, deadWallTileIds: Set<Uuid>, diceCount: Int, isNewOpening: Boolean, revealedTileIds: Set<Uuid>) = Unit

    /** 丟棄牌牆轉場。 */
    override fun publishWallLayoutTransition(gameId: Uuid, phases: List<PhysicalWallLayoutTransitionPhase>) = Unit

    /** 丟棄翻牌通知。 */
    override fun publishWallTilesRevealed(gameId: Uuid, revealedTileIds: Set<Uuid>) = Unit

    /** 丟棄規則狀態通知。 */
    override fun publishRuleStateUpdated(gameId: Uuid) = Unit

    /** 丟棄局況通知。 */
    override fun publishRoundInfoUpdated(gameId: Uuid, tableState: TableState) = Unit

    /** 丟棄玩家區域通知。 */
    override fun publishPlayerTilesUpdated(gameId: Uuid, seatIndex: Int, standingTileIds: List<Uuid>, drawnTileId: Uuid?, melds: List<MeldPresentation>, setAsideTileIds: List<Uuid>, isNewlyDrawn: Boolean, newlyClaimedMeldTileIds: Set<Uuid>, newlySetAsideTileIds: Set<Uuid>) = Unit

    /** 丟棄初始發牌通知。 */
    override fun publishInitialDeal(gameId: Uuid, handTileIdsBySeatIndex: Map<Int, List<Uuid>>, postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>, dealerSeatIndex: Int, dealBatchSizes: List<Int>, diceCount: Int) = Unit

    /** 清除玩家區域呈現。 */
    override fun clearPlayerTiles(gameId: Uuid) = Unit

    /** 丟棄開局通知。 */
    override fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>) = Unit

    /** 丟棄牌河通知。 */
    override fun publishDiscardPileUpdated(gameId: Uuid, seatIndex: Int, discardTileIds: List<Uuid>, sidewaysMarkedTileId: Uuid?, newlyDiscardedTileId: Uuid?) = Unit

    /** 丟棄胡牌慶祝通知。 */
    override fun publishWinCelebration(gameId: Uuid, request: WinCelebrationRequest) = Unit

    /** 丟棄胡牌結算通知。 */
    override fun publishWinSettlement(gameId: Uuid, request: WinSettlementPresentationRequest) = Unit
}

/** 表示沒有需要等待的呈現的無頭 gate。 */
private class NoOpBusyGate : GamePresentationBusyGate {
    /** 回報沒有進行中的呈現。 */
    override fun isBusy(gameId: Uuid) = false
}

/** 丟棄計時器更新的無頭 adapter。 */
private class NoOpTimerUpdates : DecisionTimerUpdatePublisher {
    /** 丟棄計時器更新。 */
    override suspend fun publish(targetPlayerId: Uuid, update: DecisionTimerUpdate) = Unit
}
