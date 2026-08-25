package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.RoundSettlementPresentationService
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
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val handSortPreferenceStore = HandSortPreferenceStore()
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
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
            declareTsumoUseCase = DeclareTsumoUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
            declareKanUseCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
            respondToDiscardUseCase = RespondToDiscardUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                eventPublisher,
                presentationPublisher,
            ),
            respondToKanUseCase = RespondToKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
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
            roundSettlementPresentationService = RoundSettlementPresentationService(presentationPublisher),
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
