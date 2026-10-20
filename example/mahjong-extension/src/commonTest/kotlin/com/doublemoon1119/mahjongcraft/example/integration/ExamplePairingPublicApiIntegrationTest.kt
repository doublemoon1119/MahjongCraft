package com.doublemoon1119.mahjongcraft.example.integration

import com.doublemoon1119.mahjongcraft.example.ExampleMahjongExtension
import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationAction
import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationCommand
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ReturnToRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.StartGameUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 以範例擴充自己的 registry 建立一條最小對局流程，確認宣告、摸打、流局、終局與保存資料可串接。
 *
 * 測試不載入任何內建規則，也不建立完整伺服器模擬器；只使用正式的權威 store 與公開 use case。
 */
class ExamplePairingPublicApiIntegrationTest {

    /** 驗證範例擴充能從開局走到終局，且宣告動作會進入保存資料。 */
    @Test
    fun `example extension completes isolated match and round trips authoritative history`() = runTest {
        val fixtures = Fixtures()
        val hostId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val roomId = Uuid.random()
        fixtures.store.update { state ->
            AuthoritativeStateUpdate(
                state.copy(
                    rooms = state.rooms + (
                        roomId to Room(
                            id = roomId,
                            hostId = hostId,
                            gameConfig = GameConfig(ExamplePairingRuleConfig(roundCount = 1)),
                            playerIds = listOf(hostId, otherPlayerId),
                            readyPlayerIds = listOf(otherPlayerId),
                            aiPlayerStrategyKeys = emptyMap(),
                        )
                        ),
                ),
                Unit,
            )
        }

        assertIs<Outcome.Success<*>>(fixtures.startGameUseCase(roomId, hostId))
        val started = fixtures.gameRepository.getGame(roomId)!!
        val actor = started.tableState.currentPlayer.id

        assertIs<Outcome.Success<*>>(fixtures.drawTileUseCase(roomId, actor))
        val drawn = fixtures.gameRepository.getGame(roomId)!!.tableState.currentPlayer.hand.lastDrawn
        assertTrue(drawn != null)
        assertIs<Outcome.Success<*>>(
            fixtures.extensionExecutor.execute(roomId, actor, ExamplePairingDeclarationCommand),
        )
        assertIs<Outcome.Success<*>>(fixtures.discardTileUseCase(roomId, actor, drawn.id))

        while (fixtures.gameRepository.getGame(roomId)!!.tableState.tileWall.remainingCount > 0) {
            val current = fixtures.gameRepository.getGame(roomId)!!.tableState.currentPlayer
            assertIs<Outcome.Success<*>>(fixtures.drawTileUseCase(roomId, current.id))
            val currentDraw = fixtures.gameRepository.getGame(roomId)!!.tableState.currentPlayer.hand.lastDrawn
            assertTrue(currentDraw != null)
            assertIs<Outcome.Success<*>>(fixtures.discardTileUseCase(roomId, current.id, currentDraw.id))
        }
        assertIs<Outcome.Success<*>>(fixtures.declareExhaustiveDrawUseCase(roomId))
        assertIs<Outcome.Success<*>>(fixtures.advanceRoundUseCase(roomId))
        assertIs<Outcome.Success<*>>(fixtures.returnToRoomUseCase(roomId))

        val snapshot = fixtures.store.snapshot()
        val encoded = fixtures.persistenceCodec.encode(snapshot.rooms.values, snapshot.games.values, snapshot.historyRecordingState)
        val restored = fixtures.persistenceCodec.decode(encoded)
        assertEquals(snapshot.rooms, restored.rooms)
        val facts = restored.historyRecordingState.pendingEvents.map { it.fact }
        assertTrue(
            facts.any { fact ->
                fact is HistoryFact.ActionAccepted && fact.action == GameAction.Extension(ExamplePairingDeclarationAction)
            },
        )
        assertTrue(
            facts.filterIsInstance<HistoryFact.MatchStarted>().any { fact ->
                fact.tableState.allIdentifiedTiles().any { tile -> tile.tile == Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN) }
            },
        )
    }

    /** 確認合法動作查詢與真正的自摸用例都能接受範例規則的摸牌張。 */
    @Test
    fun `example pairing tsumo returned by legal actions succeeds through flow use case`() = runTest {
        val fixtures = Fixtures()
        val hostId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val roomId = Uuid.random()
        fixtures.store.update { state ->
            AuthoritativeStateUpdate(
                state.copy(
                    rooms = state.rooms + (
                        roomId to Room(
                            id = roomId,
                            hostId = hostId,
                            gameConfig = GameConfig(ExamplePairingRuleConfig(roundCount = 1)),
                            playerIds = listOf(hostId, otherPlayerId),
                            readyPlayerIds = listOf(otherPlayerId),
                            aiPlayerStrategyKeys = emptyMap(),
                        )
                        ),
                ),
                Unit,
            )
        }

        assertIs<Outcome.Success<*>>(fixtures.startGameUseCase(roomId, hostId))
        val actor = fixtures.gameRepository.getGame(roomId)!!.tableState.currentPlayer.id
        assertIs<Outcome.Success<*>>(fixtures.drawTileUseCase(roomId, actor))

        fixtures.gameRepository.updateGame(roomId) { game ->
            val tableState = checkNotNull(game).tableState
            val currentPlayer = tableState.currentPlayer
            val standingTiles = currentPlayer.hand.tiles
            check(standingTiles.size == 3)
            val oneMan = Tile.Numeric(Tile.Suit.Character, 1)
            val twoMan = Tile.Numeric(Tile.Suit.Character, 2)
            val preparedHand = currentPlayer.hand.copy(
                tiles = standingTiles.mapIndexed { index, tile ->
                    tile.copy(tile = if (index < 2) oneMan else twoMan)
                },
                lastDrawn = currentPlayer.hand.lastDrawn?.copy(tile = twoMan),
            )
            game.copy(
                tableState = tableState.copy(
                    players = tableState.players.map { player ->
                        if (player.id == actor) player.copy(hand = preparedHand) else player
                    },
                ),
            ) to Unit
        }

        val legalActions = fixtures.getLegalActionsUseCase(roomId, actor)
        assertIs<Outcome.Success<List<GameAction>>>(legalActions)
        assertTrue(GameAction.Tsumo in legalActions.value)

        assertIs<Outcome.Success<Unit>>(fixtures.declareTsumoUseCase(roomId, actor))
        val settledPlayers = fixtures.gameRepository.getGame(roomId)!!.tableState.players
        val settledPlayer = settledPlayers.first { it.id == actor }
        assertTrue(GameAction.Tsumo in settledPlayer.actionHistory)
        assertEquals(110, settledPlayer.score)
        assertEquals(90, settledPlayers.first { it.id != actor }.score)
    }

    /**
     * 收集開局桌況中的全部牌張，包含牌山、王牌與玩家手上的牌。
     *
     * @receiver 要收集牌張的桌況。
     * @return 桌況中目前可識別的全部牌張。
     */
    private fun TableState.allIdentifiedTiles() = tileWall.getAllTiles() + reservedWallTiles + players.flatMap { it.hand.allTiles }

    /**
     * 只組裝範例規則所需的權威流程服務。
     *
     * 這個 fixture 使用獨立 registry，避免載入內建規則或依賴正式伺服器的組裝。
     */
    private class Fixtures {
        /** 啟用歷史記錄的權威狀態儲存區。 */
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, maxPendingHistoryEvents = 10_000)

        /** 連接 [store] 的權威對局 repository。 */
        val gameRepository = GameRepositoryImpl(store)

        /** 只登記範例規則的規則模組 registry。 */
        private val moduleRegistry = MahjongModuleRegistryImpl()

        /** 提供範例規則與資料 codec 登記的擴充入口。 */
        private val extension = ExampleMahjongExtension()

        /** 保存測試觀察者快照的替身。 */
        private val snapshotRepository = FakeGameSnapshotRepository()

        /** 依範例規則的可見性同步遊戲快照。 */
        private val snapshotSynchronizer = GameSnapshotSynchronizer(
            gameRepository,
            snapshotRepository,
            GameVisibilityPolicyImpl(moduleRegistry),
        )

        /** 接收測試期間產生的遊戲事件。 */
        private val gameEvents = FakeGameEventPublisher()

        /** 接收測試期間產生的呈現更新。 */
        private val presentations = FakeGamePresentationPublisher()

        /** 保存測試玩家的手牌排序偏好。 */
        private val handSort = HandSortPreferenceStore()

        /** 管理範例規則的流局後續處理器。 */
        private val postActionResolvers = PostActionExhaustiveDrawResolverRegistry().apply { freeze() }

        /** 管理範例規則提供的擴充命令。 */
        private val commandRegistry = ExtensionGameCommandExecutorRegistry().also { registry ->
            extension.registerGameCommandHandlers(registry)
            registry.freeze()
        }

        /** 執行範例規則提供的擴充命令。 */
        val extensionExecutor = ExtensionGameCommandExecutor(
            commandRegistry,
            ExtensionGameCommandContext(
                gameRepository,
                moduleRegistry,
                snapshotSynchronizer,
                handSort,
                postActionResolvers,
                gameEvents,
                presentations,
            ),
        )

        /** 啟動範例規則對局的 use case。 */
        val startGameUseCase: StartGameUseCase

        /** 執行摸牌的 use case。 */
        val drawTileUseCase: DrawTileUseCase

        /** 查詢目前玩家可執行的額外合法動作。 */
        val getLegalActionsUseCase: GetLegalActionsUseCase

        /** 執行自摸結算的 use case。 */
        val declareTsumoUseCase: DeclareTsumoUseCase

        /** 執行捨牌的 use case。 */
        val discardTileUseCase: DiscardTileUseCase

        /** 宣告荒牌流局的 use case。 */
        val declareExhaustiveDrawUseCase: DeclareExhaustiveDrawUseCase

        /** 推進流局後回合的 use case。 */
        val advanceRoundUseCase: AdvanceRoundUseCase

        /** 將已完成的對局送回房間的 use case。 */
        val returnToRoomUseCase: ReturnToRoomUseCase

        /** 編碼與解碼權威狀態保存資料的 codec。 */
        val persistenceCodec: AuthoritativeStatePersistenceCodec

        init {
            extension.registerRuleModules(moduleRegistry)
            moduleRegistry.freeze()
            startGameUseCase = StartGameUseCase(store, moduleRegistry, snapshotSynchronizer, handSort, gameEvents, presentations)
            drawTileUseCase = DrawTileUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, gameEvents, presentations)
            getLegalActionsUseCase = GetLegalActionsUseCase(gameRepository, moduleRegistry)
            declareTsumoUseCase = DeclareTsumoUseCase(
                gameRepository = gameRepository,
                moduleRegistry = moduleRegistry,
                snapshotSynchronizer = snapshotSynchronizer,
                eventPublisher = gameEvents,
                presentationPublisher = presentations,
                winPresentationHandoff = WinPresentationHandoff(),
                winCelebrationCueResolverRegistry = WinCelebrationCueResolverRegistryImpl().apply { freeze() },
                winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry().apply { freeze() },
            )
            discardTileUseCase = DiscardTileUseCase(
                gameRepository,
                moduleRegistry,
                snapshotSynchronizer,
                handSort,
                postActionResolvers,
                gameEvents,
                presentations,
            )
            declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, gameEvents)
            advanceRoundUseCase = AdvanceRoundUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, handSort, gameEvents, presentations)
            returnToRoomUseCase = ReturnToRoomUseCase(
                store,
                FakeRoomSnapshotRepository(),
                FakeRoomEventPublisher(),
                presentations,
            )
            val persistenceRegistries = emptyPersistenceRegistries()
            extension.registerPersistenceDtos(persistenceRegistries)
            persistenceRegistries.freeze()
            persistenceCodec = AuthoritativeStatePersistenceCodec(persistenceRegistries)
        }
    }
}
