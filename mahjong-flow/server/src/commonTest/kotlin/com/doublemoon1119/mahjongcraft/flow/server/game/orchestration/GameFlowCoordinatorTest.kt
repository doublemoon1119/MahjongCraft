package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementMode
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
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
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingKanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.WinPresentationSegment
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [GameFlowCoordinator] 的單元測試類別。
 *
 * 驗證三種自動銜接時機：一般流局（[GameError.WallExhausted]）、四槓散了（攔截
 * [GameCommand.Discard]/[GameCommand.Riichi]）、連莊/過莊（是否結束本局的判斷），
 * 以及不該誤觸發的一般路徑與錯誤原樣傳遞。
 */
class GameFlowCoordinatorTest {

    private val gameId = Uuid.random()

    private class Fixtures(
        winRoundContinuationResolverRegistry: WinRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry().apply { freeze() },
    ) {
        val gameRepo = FakeGameRepository()
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
                winRoundContinuationResolverRegistry,
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
            // FakeGameRepository 是獨立於 AuthoritativeStateStore 的測試替身，這裡的 ReturnToRoomUseCase
            // 因此接不到同一份對局資料，對本檔案的測試而言只是滿足建構子的無害 no-op；真的驗證
            // Game → Room 轉移的整合測試見 ReturnToRoomUseCaseTest／MahjongAutoDrawServiceTest 那種
            // 共用真正 AuthoritativeStateStore 的 Fixtures 寫法。
            returnToRoomUseCase = ReturnToRoomUseCase(AuthoritativeStateStore(), FakeRoomSnapshotRepository(), FakeRoomEventPublisher(), presentationPublisher),
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
    }

    /** 驗證進入強制自動操作後不再接受玩家手動命令。 */
    @Test
    fun `test forced auto play player command is rejected`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                id = gameId,
                players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            ),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(playerId),
        )
        fixtures.gameRepo.setGame(game)

        val result = fixtures.coordinator(gameId, playerId, GameCommand.Draw)

        assertEquals(
            Outcome.Error(GameError.ForcedAutoPlayActive(playerId, gameId)),
            result,
        )
        assertEquals(game.tableState, fixtures.gameRepo.getTableState(gameId))
    }

    /**
     * 迴歸測試：驗證強制自動操作只鎖住逾時當下那一次決策——[GameFlowCoordinator.driveAutomatedPlayers]
     * 替強制自動操作玩家送出一次自動捨牌後，該玩家必須立刻從
     * [Game.forcedAutoPlayPlayerIds] 移除，而不是被永久鎖住到對局結束（曾經的設計缺陷：一旦逾時，
     * 玩家之後每一次決策都會被伺服器代打，完全拿不回操作權）。
     */
    @Test
    fun `test forced auto play only locks the single timed out decision`() = runTest {
        val fixtures = Fixtures()
        val forcedPlayerId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val forcedPlayer = FakeMahjongPlayerFactory.create(
            id = forcedPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = lastDrawn),
        )
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val game = Game(
            tableState = FakeTableStateFactory.create(
                id = gameId,
                players = listOf(forcedPlayer, other),
                config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
                currentPlayerIndex = 0,
            ),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(forcedPlayerId),
        )
        fixtures.gameRepo.setGame(game)

        fixtures.coordinator.driveAutomatedPlayers(gameId)

        val updatedGame = fixtures.gameRepo.getGame(gameId)!!
        assertTrue(
            forcedPlayerId !in updatedGame.forcedAutoPlayPlayerIds,
            "Player must regain control after their single missed decision is auto-played.",
        )
        assertEquals(lastDrawn, updatedGame.tableState.players.first { it.id == forcedPlayerId }.discardPile.entries.single().tile)
    }

    // ---- 一般流局：WallExhausted 銜接 ----

    /**
     * 驗證任一命令回傳 [GameError.WallExhausted] 時，立即銜接一般流局並接著開下一局：
     * 呼叫端仍然看到原始的 `WallExhausted` 錯誤，但桌況已經自動流局、重新發牌。
     */
    @Test
    fun `test wall exhausted chains exhaustive draw and advance round`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, playerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.WallExhausted(gameId), result.error)
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.tileWall.remainingCount > 0, "The wall should have been rebuilt for the next hand.")
        assertTrue(newState.players.first { it.id == playerId }.actionHistory.isEmpty(), "A fresh hand's actionHistory should be empty.")
    }

    /**
     * 驗證規則不支援一般流局結算時（`declareExhaustiveDraw` 回傳 null），不會誤觸發
     * `AdvanceRoundUseCase`——桌況除了 `WallExhausted` 錯誤本身以外完全不變。
     */
    @Test
    fun `test wall exhausted does not chain advance round when exhaustive draw is unsupported`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = TaiwanRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, playerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.WallExhausted(gameId), result.error)
        assertEquals(table, fixtures.gameRepo.getTableState(gameId))
    }

    // ---- 四槓散了：攔截 Discard/Riichi ----

    private fun kanMeldsOf(vararg tileValues: Tile): List<Meld> = tileValues.map { tile ->
        val tiles = List(4) { FakeIdentifiedTileFactory.create(tile) }
        Meld(MeldType.CLOSED_KAN, tiles, sourceTile = null, sourceDirection = RelativeDirection.Self)
    }

    private fun suukanNagareTable(dealerId: Uuid, otherId: Uuid, dealerLastDrawn: IdentifiedTile): TableState {
        val dealer = FakeMahjongPlayerFactory.create(
            id = dealerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = kanMeldsOf(Tile.Honor.East, Tile.Honor.South), lastDrawn = dealerLastDrawn),
        )
        val other = FakeMahjongPlayerFactory.create(
            id = otherId,
            initialSeat = Wind.SOUTH,
            hand = Hand(melds = kanMeldsOf(Tile.Honor.West, Tile.Honor.North)),
        )
        return FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, other),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            currentPlayerIndex = 0,
        )
    }

    /**
     * 驗證四槓散了成立（4 個槓子分屬不同玩家）時，[GameCommand.Discard] 會被攔截、改觸發四槓散了
     * 流局並接著開下一局——原本要打出的牌不會真的進牌河（副露被重置就是證明，正常捨牌不會清空
     * 既有的槓子副露）；莊家固定連莊（`comboCount + 1`、莊家方位不變）。
     */
    @Test
    fun `test discard command is redirected to suukan nagare when pending`() = runTest {
        val fixtures = Fixtures()
        val dealerId = Uuid.random()
        val otherId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        fixtures.gameRepo.setTableState(suukanNagareTable(dealerId, otherId, lastDrawn))

        val result = fixtures.coordinator(gameId, dealerId, GameCommand.Discard(lastDrawn.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(1, newState.comboCount, "Suukan nagare is an abortive draw; the dealer always repeats.")
        assertEquals(Wind.EAST, newState.players.first { it.id == dealerId }.currentWind)
        assertTrue(newState.players.first { it.id == dealerId }.hand.melds.isEmpty(), "A fresh hand should have no melds left over.")
    }

    /**
     * 驗證同樣的攔截也適用於 [GameCommand.Riichi]（立直宣告本身就包含打出一張牌）。
     */
    @Test
    fun `test riichi command is redirected to suukan nagare when pending`() = runTest {
        val fixtures = Fixtures()
        val dealerId = Uuid.random()
        val otherId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        fixtures.gameRepo.setTableState(suukanNagareTable(dealerId, otherId, lastDrawn))

        val result = fixtures.coordinator(
            gameId,
            dealerId,
            GameCommand.Extension(com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand(lastDrawn.id)),
        )

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(1, newState.comboCount)
        assertTrue(newState.players.first { it.id == dealerId }.hand.melds.isEmpty())
    }

    /**
     * 驗證槓子總數未滿 4 個時，四槓散了不成立，[GameCommand.Discard] 正常執行（不被攔截）。
     */
    @Test
    fun `test discard command proceeds normally when suukan nagare is not pending`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = lastDrawn))
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, playerId, GameCommand.Discard(lastDrawn.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount, "No hand-ending event occurred; the round should not have advanced.")
        assertEquals(lastDrawn, newState.players.first { it.id == playerId }.discardPile.entries.single().tile)
    }

    // ---- 連莊/過莊：一定結束本局的命令 ----

    // 中中、發發發、白白白、123m、55p（大三元役滿，13 張立牌）
    private val daisangenTiles = listOf(
        Tile.Honor.Red, Tile.Honor.Red,
        Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
        Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 5),
    )

    /**
     * 驗證自摸成功一定結束本局，`AdvanceRoundUseCase` 會被銜接（新的一手牌已重新發好、
     * `actionHistory` 已重置）。
     */
    @Test
    fun `test tsumo command chains advance round`() = runTest {
        val fixtures = Fixtures()
        val winnerId = Uuid.random()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = daisangenTiles.map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = winningTile),
            discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = 25000)
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH, playerRuleState = RiichiPlayerState()).copy(score = 25000)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(winner, other), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, winnerId, GameCommand.Tsumo)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == winnerId }.actionHistory.isEmpty(), "A fresh hand's actionHistory should be empty.")
        assertEquals(1, newState.comboCount, "The winner is the dealer, so the dealer should repeat.")
        // 胡牌演出由 use case 交給 handoff、再由 coordinator 在取得 EndRound 後立即發布到整桌共用
        // 時間軸——這是既有規則一直以來的可觀察行為，只是呼叫點從 use case 內部移到了這裡。
        val celebrations = fixtures.presentationPublisher.getPublishedWinCelebrations(gameId)
        assertEquals(1, celebrations.size)
        assertEquals(winningTile.id, celebrations.single().winningTileId)
        assertTrue(celebrations.single().isTsumo)
        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertTrue(!published.roundContinues, "The round ended, so the whole presentation owns the table.")
        assertEquals(
            listOf(listOf(WinPresentationSegment.CELEBRATION, WinPresentationSegment.SETTLEMENT)),
            fixtures.presentationPublisher.getWinPresentationSegmentOrder(gameId),
            "Celebration must always be ordered before settlement.",
        )
        assertEquals(null, fixtures.winPresentationHandoff.take(gameId, setOf(winnerId)), "The handoff must be consumed.")
    }

    /**
     * 驗證規則模組登記了 [WinRoundContinuationResolver] 且回傳 [com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective.ContinueRound]
     * 時，自摸不會結束本局：不銜接 `AdvanceRoundUseCase`（`pendingTransition` 維持 null、贏家的
     * `Tsumo` 記錄原樣保留，不會被新一手牌重置），改為原子套用 `finishedPlayerIds`／
     * `currentPlayerIndex` 的變化。
     */
    @Test
    fun `test tsumo command does not chain advance round when a resolver returns ContinueRound`() = runTest {
        val winnerId = Uuid.random()
        val otherId = Uuid.random()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val fixtures = runContinuingWinTsumo(
            winnerId = winnerId,
            otherId = otherId,
            winningTile = winningTile,
            settlementMode = ContinuingWinSettlementMode.FULL,
        )
        val game = fixtures.gameRepo.getGame(gameId)!!
        assertEquals(null, game.pendingTransition, "ContinueRound must not chain AdvanceRound.")
        val newState = game.tableState
        assertTrue(
            newState.players.first { it.id == winnerId }.actionHistory.any { it is GameAction.Tsumo },
            "The hand did not end, so the Tsumo record must not be reset by a fresh deal.",
        )
        assertEquals(setOf(winnerId), newState.finishedPlayerIds)
        assertEquals(1, newState.currentPlayerIndex, "Turn should be handed to nextPlayerId from the directive.")

        // FULL 模式：演出照樣發布，但改走中途胡牌專用時間軸——它不列入整桌忙碌判定，因此其他仍在
        // 本局中的玩家可以繼續摸打，只有換局要等它播完。
        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertTrue(published.roundContinues, "A continuing win must tell the platform the round goes on.")
        assertTrue(
            published.hasWatchableShowcase,
            "Daisangen is a yakuman, so this continuing win still has a showcase that must be watched.",
        )
        assertEquals(setOf(winnerId), published.winnerPlayerIds)
        assertEquals(
            listOf(listOf(WinPresentationSegment.CELEBRATION, WinPresentationSegment.SETTLEMENT)),
            fixtures.presentationPublisher.getWinPresentationSegmentOrder(gameId),
            "Celebration must always be ordered before settlement.",
        )
        assertEquals(null, fixtures.winPresentationHandoff.take(gameId, setOf(winnerId)), "The handoff must be consumed.")

        // 中途胡牌演出還在播時本局不該換局；播完後才會真的推進。
        fixtures.gameRepo.updateGame(gameId) { current ->
            current!!.copy(pendingTransition = PendingGameTransition.AdvanceRound) to Unit
        }
        fixtures.presentationBusyGate.setPresentingContinuingWin(gameId, true)
        assertTrue(!fixtures.coordinator.resumePendingGameTransition(gameId))
        fixtures.presentationBusyGate.setPresentingContinuingWin(gameId, false)
        assertTrue(
            fixtures.coordinator.resumePendingGameTransition(gameId),
            "Once the queue drains, the pending round transition should finally run.",
        )
    }

    /**
     * 驗證 [ContinuingWinSettlementMode.BRIEF]：胡牌演出**完全照常**（理牌、攤牌、降臨特效，役滿時還有
     * showcase），只有結算面板換成精簡版。
     *
     * 這正是這組 enum 的設計重點：胡牌演出是「這個人胡了、退出本局」在世界裡唯一的視覺訊號，其他仍在
     * 局中的玩家必須看見，任何模式都不可省略；真正依情境調整的只有面板，因為面板是要**讀**的。
     */
    @Test
    fun `test continuing win in brief mode only shrinks the settlement panel`() = runTest {
        val fixtures = runContinuingWinTsumo(
            winnerId = Uuid.random(),
            otherId = Uuid.random(),
            winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red),
            settlementMode = ContinuingWinSettlementMode.BRIEF,
        )

        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertTrue(published.settlement.isBrief, "BRIEF must ask the platform for the shortened panel.")
        assertTrue(published.roundContinues)
        assertEquals(
            listOf(listOf(WinPresentationSegment.CELEBRATION, WinPresentationSegment.SETTLEMENT)),
            fixtures.presentationPublisher.getWinPresentationSegmentOrder(gameId),
            "The win celebration is never skipped, whatever the settlement mode.",
        )
        // 手牌是大三元役滿：即使面板精簡，showcase 照樣播、照樣暫停全桌。
        assertTrue(
            published.hasWatchableShowcase,
            "A yakuman showcase must still be watched even when the panel is brief.",
        )
    }

    // 234m 567m 234p 567p 5s（斷么九，13 張立牌）——一般役，**不是**役滿，因此不會有 showcase cue。
    private val tanyaoTiles = listOf(
        Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3), Tile.Numeric(Tile.Suit.Character, 4),
        Tile.Numeric(Tile.Suit.Character, 5), Tile.Numeric(Tile.Suit.Character, 6), Tile.Numeric(Tile.Suit.Character, 7),
        Tile.Numeric(Tile.Suit.Dot, 2), Tile.Numeric(Tile.Suit.Dot, 3), Tile.Numeric(Tile.Suit.Dot, 4),
        Tile.Numeric(Tile.Suit.Dot, 5), Tile.Numeric(Tile.Suit.Dot, 6), Tile.Numeric(Tile.Suit.Dot, 7),
        Tile.Numeric(Tile.Suit.Bamboo, 5),
    )

    /**
     * 驗證**一般**（非役滿）中途胡牌不需要中斷遊戲：沒有 showcase cue，因此
     * `hasWatchableShowcase == false`，平台不會暫停其他仍在本局中的玩家。
     */
    @Test
    fun `test ordinary continuing win does not need to pause the other players`() = runTest {
        val fixtures = runContinuingWinTsumo(
            winnerId = Uuid.random(),
            otherId = Uuid.random(),
            winningTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
            settlementMode = ContinuingWinSettlementMode.FULL,
            handTiles = tanyaoTiles,
        )

        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertTrue(published.roundContinues)
        assertNotNull(published.celebration)
        assertTrue(
            published.celebration!!.winners.all { it.cue == null },
            "Tanyao is not a yakuman, so no showcase cue should be resolved.",
        )
        assertTrue(
            !published.hasWatchableShowcase,
            "An ordinary continuing win must not pause the players who are still in the round.",
        )
    }

    /**
     * 驗證含役滿 cue 的中途胡牌會被標記成「需要觀看」——平台據此暫停玩家／AI／強制自動操作／決策
     * 計時器直到 showcase 播完（見 [WinPresentationRequest] KDoc）。
     *
     * 跟上一個測試的唯一差別就是手牌：大三元是役滿，`cue` 只在役滿成立時才非 null。
     */
    @Test
    fun `test continuing win carrying a yakuman cue is marked as needing to pause play`() = runTest {
        val fixtures = runContinuingWinTsumo(
            winnerId = Uuid.random(),
            otherId = Uuid.random(),
            winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red),
            settlementMode = ContinuingWinSettlementMode.FULL,
        )

        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertTrue(published.roundContinues, "The round continues, so most of this presentation must not block.")
        assertNotNull(published.celebration)
        assertTrue(
            published.celebration!!.winners.any { it.cue != null },
            "Daisangen is a yakuman, so the celebration must carry a showcase cue.",
        )
        assertTrue(
            published.hasWatchableShowcase,
            "A continuing win with a yakuman showcase must still pause players for that segment.",
        )
    }

    /**
     * 驗證同一局內連續兩次中途胡牌各自送出一次完整呈現請求，順序與贏家都正確——排隊播放本身由平台的
     * 呈現時間軸負責（見 `FabricGamePresentationPublisher`），這裡驗證的是 flow 層確實逐次送出、
     * 沒有把兩次合併或漏掉任何一次。
     */
    @Test
    fun `test consecutive continuing wins each publish their own presentation in order`() = runTest {
        val firstWinnerId = Uuid.random()
        val secondWinnerId = Uuid.random()
        val fixtures = runContinuingWinTsumo(
            winnerId = firstWinnerId,
            otherId = secondWinnerId,
            winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red),
            settlementMode = ContinuingWinSettlementMode.FULL,
        )
        // 第二位玩家接著自摸：沿用同一份 fixtures（含同一個 resolver registry）再跑一次。
        val secondWinningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        fixtures.gameRepo.updateGame(gameId) { game ->
            val state = game!!.tableState
            val updated = state.players.map { player ->
                if (player.id == secondWinnerId) {
                    player.copy(hand = Hand(tiles = daisangenTiles.map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = secondWinningTile))
                } else {
                    player
                }
            }
            game.copy(tableState = state.copy(players = updated)) to Unit
        }

        fixtures.coordinator(gameId, secondWinnerId, GameCommand.Tsumo)

        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId)
        assertEquals(2, published.size, "Each continuing win must publish its own presentation, never merged.")
        assertEquals(setOf(firstWinnerId), published[0].winnerPlayerIds)
        assertEquals(setOf(secondWinnerId), published[1].winnerPlayerIds)
        assertEquals(
            List(2) { listOf(WinPresentationSegment.CELEBRATION, WinPresentationSegment.SETTLEMENT) },
            fixtures.presentationPublisher.getWinPresentationSegmentOrder(gameId),
            "Celebration must precede settlement within every publish.",
        )
    }

    /**
     * 以指定的 [settlementMode] 跑一次「規則判定本局繼續」的自摸，回傳執行後的 fixtures 供斷言。
     *
     * 兩種結算面板模式的測試共用同一份桌況與 resolver 設定，差別只在 [settlementMode]。
     */
    private suspend fun runContinuingWinTsumo(
        winnerId: Uuid,
        otherId: Uuid,
        winningTile: com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile,
        settlementMode: ContinuingWinSettlementMode,
        handTiles: List<Tile> = daisangenTiles,
    ): Fixtures {
        val ruleModuleId = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }.getModule(RiichiRuleConfig()).id
        val continuationRegistry = WinRoundContinuationResolverRegistry().apply {
            register(
                object : WinRoundContinuationResolver {
                    override val id: String = "test:continue"
                    override val ruleModuleId: String = ruleModuleId
                    override val priority: Int = 0

                    override fun resolve(
                        context: WinRoundContinuationContext,
                        ruleModule: com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule<*>,
                    ): WinRoundDirective {
                        // 從 context 取本次贏家，讓同一份 registry 能重複用於同一局內的連續胡牌。
                        val settled = context.settledTableState
                        val nextActive = settled.players
                            .first { it.id !in context.winnerPlayerIds && settled.isPlayerActive(it.id) }
                        return WinRoundDirective.ContinueRound(
                            newlyFinishedPlayerIds = context.winnerPlayerIds,
                            nextPlayerId = nextActive.id,
                            settlementMode = settlementMode,
                        )
                    }
                },
            )
            freeze()
        }
        val fixtures = Fixtures(winRoundContinuationResolverRegistry = continuationRegistry)
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = handTiles.map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = winningTile),
            discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = 25000)
        val other = FakeMahjongPlayerFactory.create(id = otherId, initialSeat = Wind.SOUTH, playerRuleState = RiichiPlayerState()).copy(score = 25000)
        // 三人桌：連續兩次中途胡牌之後仍必須留下至少一位 active 玩家，否則 ContinueRound 不合法。
        val bystander = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST, playerRuleState = RiichiPlayerState()).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner, other, bystander),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, winnerId, GameCommand.Tsumo)
        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        return fixtures
    }

    /**
     * 驗證九種九牌宣告成功一定結束本局，`AdvanceRoundUseCase` 會被銜接。
     */
    @Test
    fun `test kyuushu kyuuhai command chains advance round`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        // 9 種以上么九牌：1m/9m/1p/9p/1s/9s/東/南/西
        val kyuushuTiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1), Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Dot, 1), Tile.Numeric(Tile.Suit.Dot, 9),
            Tile.Numeric(Tile.Suit.Bamboo, 1), Tile.Numeric(Tile.Suit.Bamboo, 9),
            Tile.Honor.East, Tile.Honor.South, Tile.Honor.West,
            Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Character, 4), Tile.Numeric(Tile.Suit.Character, 5),
        )
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = kyuushuTiles.map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = drawn),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, playerId, GameCommand.DeclareExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == playerId }.actionHistory.isEmpty())
        assertEquals(1, newState.comboCount, "Kyuushu kyuuhai is an abortive draw; the dealer always repeats.")
    }

    // ---- 連莊/過莊：視分支結果的命令 ----

    /**
     * 驗證一般捨牌、無人反應時不會結束本局，`AdvanceRoundUseCase` 不會被誤觸發。
     */
    @Test
    fun `test ordinary discard does not chain advance round`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val bystanderId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = lastDrawn))
        val bystander = FakeMahjongPlayerFactory.create(id = bystanderId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player, bystander), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, playerId, GameCommand.Discard(lastDrawn.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        assertEquals(GameAction.Discard(lastDrawn.id), newState.players.first { it.id == playerId }.actionHistory.last())
    }

    /**
     * 驗證捨牌觸發內嵌的四風連打時，`AdvanceRoundUseCase` 會被銜接。
     */
    @Test
    fun `test discard triggering suufon renda chains advance round`() = runTest {
        val fixtures = Fixtures()
        val p1Id = Uuid.random()
        val p2Id = Uuid.random()
        val p1FirstDiscard = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val p2LastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val p1 = FakeMahjongPlayerFactory.create(
            id = p1Id,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(p1FirstDiscard),
        )
        val p2 = FakeMahjongPlayerFactory.create(
            id = p2Id,
            initialSeat = Wind.SOUTH,
            hand = Hand(lastDrawn = p2LastDrawn),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(p1, p2), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 1)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, p2Id, GameCommand.Discard(p2LastDrawn.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == p2Id }.actionHistory.isEmpty())
        assertEquals(1, newState.comboCount, "Suufon renda is an abortive draw; the dealer always repeats.")
    }

    private fun discardReactionTable(discarderId: Uuid, respondentId: Uuid, respondentHand: Hand): TableState {
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        // score 給一個一般起始分數（而非工廠預設的 0），避免放銃付款後分數跌破 0 誤觸「擊飛」
        // （MahjongRuleModule.hasAdditionalMatchEndCondition）而讓對局提早結束，干擾這裡真正要測的
        // 「Ron 後有沒有正確銜接 AdvanceRoundUseCase」。
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        ).copy(score = 25000)
        val respondent = FakeMahjongPlayerFactory.create(id = respondentId, initialSeat = Wind.SOUTH, hand = respondentHand, playerRuleState = RiichiPlayerState())
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(respondentId)),
        )
        return table
    }

    // 役牌發已成立（1 翻）、單騎聽白（欲榮和的那張牌）
    private fun ronReadyHand(): Hand = Hand(
        tiles = listOf(
            Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
            Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3), Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Dot, 5), Tile.Numeric(Tile.Suit.Dot, 6), Tile.Numeric(Tile.Suit.Dot, 7),
            Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7), Tile.Numeric(Tile.Suit.Bamboo, 8),
        ).map { FakeIdentifiedTileFactory.create(it) } + FakeIdentifiedTileFactory.create(Tile.Honor.White),
    )

    /**
     * 驗證 [RespondToDiscardUseCase] 解析為榮和時，`AdvanceRoundUseCase` 會被銜接。
     */
    @Test
    fun `test respond to discard resolving as ron chains advance round`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val table = discardReactionTable(discarderId, respondentId, ronReadyHand())
        fixtures.gameRepo.setTableState(table)
        val whiteTileId = table.pendingReaction!!.tileId

        val result = fixtures.coordinator(gameId, respondentId, GameCommand.RespondToDiscard(GameAction.Ron(whiteTileId)))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == respondentId }.actionHistory.isEmpty())
    }

    /**
     * 迴歸測試：驗證榮和呈現尚未完成時不會先進入下一局；呈現恢復閒置後才銜接
     * [AdvanceRoundUseCase]。
     */
    @Test
    fun `test ron waits for presentation before chaining advance round`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val table = discardReactionTable(discarderId, respondentId, ronReadyHand())
        fixtures.gameRepo.setTableState(table)
        fixtures.presentationBusyGate.setBusy(gameId, true)
        val whiteTileId = table.pendingReaction!!.tileId

        val result = fixtures.coordinator(gameId, respondentId, GameCommand.RespondToDiscard(GameAction.Ron(whiteTileId)))

        assertTrue(result is Outcome.Success)
        val settledState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(
            settledState.players.first { it.id == respondentId }.actionHistory.any { it is GameAction.Ron },
            "Winning settlement should be retained until the presentation finishes.",
        )
        assertEquals(PendingGameTransition.AdvanceRound, fixtures.gameRepo.getGame(gameId)!!.pendingTransition)

        fixtures.presentationBusyGate.setBusy(gameId, false)
        assertTrue(fixtures.coordinator.resumePendingGameTransition(gameId))
        val advancedState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(advancedState.players.first { it.id == respondentId }.actionHistory.isEmpty())
    }

    /**
     * 迴歸測試：模擬 server 在榮和結算與待推進流程已持久化、但呈現尚未結束時重啟；
     * 新 session 可單純從權威桌況補完推進，且重複恢復不會再推進一次。
     */
    @Test
    fun `test persisted ron settlement resumes round transition after restart`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val table = discardReactionTable(discarderId, respondentId, ronReadyHand())
        fixtures.gameRepo.setTableState(table)
        val whiteTileId = table.pendingReaction!!.tileId

        val settlement = fixtures.router(
            gameId,
            respondentId,
            GameCommand.RespondToDiscard(GameAction.Ron(whiteTileId)),
        )
        assertTrue(settlement is Outcome.Success)
        assertTrue(
            fixtures.gameRepo.getTableState(gameId)!!.players
                .first { it.id == respondentId }
                .actionHistory
                .any { it is GameAction.Ron },
        )
        fixtures.gameRepo.updateGame(gameId) { game ->
            game!!.copy(pendingTransition = PendingGameTransition.AdvanceRound) to Unit
        }

        assertTrue(fixtures.coordinator.resumePendingGameTransition(gameId))
        val advancedState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(advancedState.players.first { it.id == respondentId }.actionHistory.isEmpty())
        assertTrue(!fixtures.coordinator.resumePendingGameTransition(gameId))
    }

    /**
     * 驗證 [RespondToDiscardUseCase] 解析為碰（未結束本局）時，`AdvanceRoundUseCase` 不會被誤觸發。
     */
    @Test
    fun `test respond to discard resolving as pon does not chain advance round`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val whiteTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val whiteTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val filler = (1..10).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1)) }
        val table = discardReactionTable(discarderId, respondentId, Hand(tiles = listOf(whiteTile1, whiteTile2) + filler))
        fixtures.gameRepo.setTableState(table)
        val whiteTileId = table.pendingReaction!!.tileId

        val result = fixtures.coordinator(gameId, respondentId, GameCommand.RespondToDiscard(GameAction.Pon(whiteTileId)))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        assertEquals(GameAction.Pon(whiteTileId), newState.players.first { it.id == respondentId }.actionHistory.last())
    }

    private fun chankanTable(declarerId: Uuid, robberId: Uuid, initialDeadWall: List<IdentifiedTile>, robberHand: Hand): TableState {
        val whiteTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val whiteTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val whiteTile3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val robbedWhiteTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.PON, listOf(whiteTile1, whiteTile2, whiteTile3), sourceTile = whiteTile3, sourceDirection = RelativeDirection.Left)
        val kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedWhiteTile.id, emptyList())
        // score 理由同 discardReactionTable：避免放槍付款後分數跌破 0 誤觸擊飛。
        val declarer = FakeMahjongPlayerFactory.create(
            id = declarerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = robbedWhiteTile),
        ).copy(score = 25000)
        val robber = FakeMahjongPlayerFactory.create(id = robberId, initialSeat = Wind.SOUTH, hand = robberHand, playerRuleState = RiichiPlayerState())
        return FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            initialDeadWall = initialDeadWall,
            currentPlayerIndex = 0,
            pendingKanReaction = PendingKanReaction(declarerId, kanAction, robbedWhiteTile, setOf(robberId)),
        )
    }

    /**
     * 驗證 [RespondToKanUseCase] 解析為榮和時，`AdvanceRoundUseCase` 會被銜接。
     */
    @Test
    fun `test respond to chankan resolving as ron chains advance round`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val robberId = Uuid.random()
        val table = chankanTable(declarerId, robberId, emptyList(), ronReadyHand())
        fixtures.gameRepo.setTableState(table)
        val robbedTileId = table.pendingKanReaction!!.robbedTile.id

        val result = fixtures.coordinator(gameId, robberId, GameCommand.RespondToKan(GameAction.Ron(robbedTileId)))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == robberId }.actionHistory.isEmpty())
    }

    /**
     * 驗證 [RespondToKanUseCase] 全員放過、補做套用副露（未結束本局）時，`AdvanceRoundUseCase`
     * 不會被誤觸發。
     */
    @Test
    fun `test respond to chankan resolving as all pass does not chain advance round`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val robberId = Uuid.random()
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val table = chankanTable(declarerId, robberId, listOf(rinshanTile), ronReadyHand())
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, robberId, GameCommand.RespondToKan(GameAction.Pass))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        val declarer = newState.players.first { it.id == declarerId }
        assertEquals(MeldType.ADDED_KAN, declarer.hand.melds.single().type, "The all-pass resume should have applied the kan.")
        assertTrue(declarer.actionHistory.isNotEmpty(), "The kan/draw should still be recorded; the hand did not end.")
    }

    // ---- 一般路徑：不該誤觸發 ----

    /**
     * 驗證一般摸牌成功（無牌山摸盡）不會觸發任何銜接。
     */
    @Test
    fun `test ordinary draw does not chain anything`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val drawnTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            tileWall = TileWall(listOf(drawnTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, playerId, GameCommand.Draw)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        assertEquals(drawnTile, newState.players.first { it.id == playerId }.hand.lastDrawn)
        assertEquals(
            PlayerDecisionPhase.OWN_TURN,
            fixtures.decisionTimerManager.getStatuses(gameId).getValue(playerId).phase,
        )
    }

    /**
     * 驗證一般槓牌成功（無搶槓視窗開啟）不會觸發任何銜接。
     */
    @Test
    fun `test ordinary kan does not chain anything`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val east1 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east2 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east3 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east4 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(tiles = listOf(east1, east2, east3), lastDrawn = east4))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            initialDeadWall = listOf(rinshanTile),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, playerId, GameCommand.Kan(GameAction.KanType.CLOSED_KAN, east4.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        assertEquals(MeldType.CLOSED_KAN, newState.players.first { it.id == playerId }.hand.melds.single().type)
    }

    // ---- 錯誤原樣傳遞 ----

    /**
     * 驗證非 `WallExhausted` 的錯誤原樣回傳，不觸發任何銜接呼叫，桌況完全不變。
     */
    @Test
    fun `test non wall exhausted errors pass through untouched`() = runTest {
        val fixtures = Fixtures()
        val currentPlayerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val currentPlayer = FakeMahjongPlayerFactory.create(id = currentPlayerId, initialSeat = Wind.EAST)
        val other = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(currentPlayer, other), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, otherPlayerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.NotPlayersTurn(otherPlayerId, gameId), result.error)
        assertEquals(table, fixtures.gameRepo.getTableState(gameId))
    }

    /** 驗證失敗命令只校正決策狀態，不會重新開始目前玩家已存在的基本思考時間。 */
    @Test
    fun `test failed command does not reset active decision timer`() = runTest {
        val fixtures = Fixtures()
        val currentPlayerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val drawnTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawnTile),
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        fixtures.gameRepo.setTableState(
            FakeTableStateFactory.create(
                id = gameId,
                players = listOf(currentPlayer, otherPlayer),
                config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            ),
        )
        fixtures.decisionTimerManager.reconcile(gameId)
        fixtures.clock.nowMillis = 2_000L

        val result = fixtures.coordinator(gameId, otherPlayerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(
            3_000L,
            fixtures.decisionTimerManager.getStatuses(gameId).getValue(currentPlayerId).time.baseRemainingMillis,
        )
    }

    // ---- AI 自動出手 ----

    /**
     * 驗證人類捨牌後、輪到的下一位是 AI 且無人可反應時：同一次 `coordinator(...)` 呼叫內，AI
     * 已經自動摸牌並捨牌，回合正確推回人類——不需要呼叫端再送出任何命令。
     */
    @Test
    fun `test ai automatically draws and discards after human discard advances turn to it`() = runTest {
        val fixtures = Fixtures()
        val humanId = Uuid.random()
        val aiId = Uuid.random()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val human = FakeMahjongPlayerFactory.create(id = humanId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = discardedTile))
        // 全是條子，跟人類打出的餅牌無關，確保不會意外開啟反應視窗
        val aiHandTiles = (1..13).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, ((it - 1) % 9) + 1)) }
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = aiHandTiles),
            aiStrategyKey = RandomAiStrategy.KEY,
            playerRuleState = RiichiPlayerState(),
        )
        val drawnTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(human, ai),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            tileWall = TileWall(listOf(drawnTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, humanId, GameCommand.Discard(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val updatedAi = newState.players.first { it.id == aiId }
        assertEquals(1, updatedAi.discardPile.entries.size, "The AI should have automatically drawn and discarded.")
        assertEquals(0, newState.currentPlayerIndex, "Turn should have advanced back to the human.")
    }

    /**
     * 驗證人類捨牌開啟反應視窗、視窗裡唯一有資格者是 AI 時：同一次呼叫內 AI 自動回應，視窗正確
     * 關閉，不需要呼叫端再送出任何命令。
     */
    @Test
    fun `test ai automatically resolves a reaction window it is the sole eligible responder for`() = runTest {
        val fixtures = Fixtures()
        val humanId = Uuid.random()
        val aiId = Uuid.random()
        val southTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val human = FakeMahjongPlayerFactory.create(id = humanId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = southTile))
        val southTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val southTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val filler = (1..10).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1)) }
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(southTile1, southTile2) + filler),
            aiStrategyKey = RandomAiStrategy.KEY,
            playerRuleState = RiichiPlayerState(),
        )
        // 若 AI 選擇過牌（而非碰），輪到的下一位就是它自己、需要先摸牌——牌山至少要有 1 張牌，
        // 否則會撞上牌山摸盡 → 一般流局 → 連莊/過莊判定，讓這個測試意外變成在測完全不同的情境。
        val nextDrawTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(human, ai),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            tileWall = TileWall(listOf(nextDrawTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator(gameId, humanId, GameCommand.Discard(southTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(null, newState.pendingReaction, "The AI should have automatically resolved the reaction window.")
    }

    /**
     * 驗證整場對局已經結束（下一次過莊判定就會讓 isMatchOver 成立）、且當前玩家恰好是 AI 且尚未
     * 摸牌時，`driveAutomatedPlayers` 能偵測到桌況沒有任何進展（摸牌因牌山已空觸發 WallExhausted →
     * 流局銜接 → 推進嘗試因 isMatchOver 而維持桌況不變）並提前跳出迴圈，而不是跑滿 100 次的迭代
     * 上限——改用「偵測沒有進展」取代單純固定次數上限，取代過去（僅靠 100 次上限）的做法。
     */
    @Test
    fun `test driveAutomatedPlayers stops early when table state makes no progress`() = runTest {
        val fixtures = Fixtures()
        val aiId = Uuid.random()
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.EAST,
            aiStrategyKey = RandomAiStrategy.KEY,
            playerRuleState = RiichiPlayerState(),
        )
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        // 一局制（OneGame，totalRounds = 1）且已經是 roundNumber = 1，下一次過莊判定就會讓
        // isMatchOver 成立；牌山已空，AI 輪到自己回合但尚未摸牌，會不斷被判斷「該幫它摸牌」。
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(ai, other),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.OneGame),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
            roundNumber = 1,
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.coordinator.driveAutomatedPlayers(gameId)

        assertTrue(
            fixtures.gameRepo.getTableStateCallCount < 20,
            "Should stop after detecting no progress on the first iteration, not loop anywhere near the " +
                "100-iteration cap (actual call count: ${fixtures.gameRepo.getTableStateCallCount}).",
        )
    }

    /**
     * 迴歸測試：驗證對局結束後（[Game.isMatchOver] 成立）再次呼叫 [GameFlowCoordinator.driveAutomatedPlayers]
     * 不會重複觸發流局結算——[Game.isMatchOver] 加入前，`AiTurnDriver`／`ForcedAutoPlayDriver` 不知道
     * 對局已經結束，會不斷嘗試對已空的牌山摸牌、不斷重新觸發 `DeclareExhaustiveDrawUseCase`，導致
     * 流局點數被重複套用；這支測試模擬「心跳每個 tick 都呼叫一次」的情境，驗證第二次呼叫之後分數
     * 不會再變動。
     */
    @Test
    fun `test driveAutomatedPlayers does not re-apply exhaustive draw settlement after match is over`() = runTest {
        val fixtures = Fixtures()
        val aiId = Uuid.random()
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.EAST,
            aiStrategyKey = RandomAiStrategy.KEY,
            playerRuleState = RiichiPlayerState(),
        )
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH, playerRuleState = RiichiPlayerState())
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(ai, other),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.OneGame),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
            roundNumber = 1,
        )
        fixtures.gameRepo.setTableState(table)

        // 第一次呼叫：觸發 WallExhausted → 流局結算 → isMatchOver 成立。
        fixtures.coordinator.driveAutomatedPlayers(gameId)
        val scoresAfterFirstCall = fixtures.gameRepo.getTableState(gameId)!!.players.associate { it.id to it.score }
        assertTrue(fixtures.gameRepo.getGame(gameId)!!.isMatchOver, "Match should be over after the wall is exhausted in a one-round match.")

        // 模擬心跳每個 tick 都再呼叫一次：分數不該再變動。
        repeat(5) { fixtures.coordinator.driveAutomatedPlayers(gameId) }
        val scoresAfterMoreCalls = fixtures.gameRepo.getTableState(gameId)!!.players.associate { it.id to it.score }

        assertEquals(scoresAfterFirstCall, scoresAfterMoreCalls, "Scores must not change after the match has already ended.")
    }
}

/** coordinator 計時整合測試使用的可控單調時間來源。 */
private class MutableMonotonicClock : MonotonicClock {
    /** 目前回傳的單調時間毫秒數。 */
    var nowMillis: Long = 0L

    /** 回傳測試指定的單調時間。 */
    override fun nowMillis(): Long = nowMillis
}
