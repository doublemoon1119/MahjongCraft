package com.doublemoon1119.mahjongcraft.example.action

import com.doublemoon1119.mahjongcraft.example.ExampleMahjongExtension
import com.doublemoon1119.mahjongcraft.example.exampleComponentRegistry
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableChange
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAndDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.AuthoritativeComponentValidatorImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.initializeRuleComponents
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializationResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.testRoundRandomness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** [ExamplePairingRevealHandHandler] 的權威驗證測試。 */
class ExamplePairingRevealHandHandlerTest {
    /** 成功攤牌後再次攤牌應被拒絕，且不得新增歷史動作。 */
    @Test
    fun `hand reveal is accepted once and duplicate is rejected without another history event`() = runTest {
        val playerId = Uuid.random()
        val table = tableState(playerId, drawn = true)
        val fixtures = Fixtures(table)
        fixtures.install()

        assertIs<Outcome.Success<*>>(fixtures.execute(playerId))
        val afterFirst = fixtures.store.snapshot()
        val actionCount = afterFirst.games.getValue(table.id).tableState.players
            .first { it.id == playerId }.actionHistory.size
        val historyCount = afterFirst.historyRecordingState.pendingEvents.size
        val revealedTable = afterFirst.games.getValue(table.id).tableState
        val change = assertNotNull(HistoryTableChange.between(table, revealedTable))
        assertEquals(revealedTable, change.applyTo(table))

        assertIs<Outcome.Error<*>>(fixtures.execute(playerId))
        val afterDuplicate = fixtures.store.snapshot()
        assertEquals(actionCount, afterDuplicate.games.getValue(table.id).tableState.players.first { it.id == playerId }.actionHistory.size)
        assertEquals(historyCount, afterDuplicate.historyRecordingState.pendingEvents.size)
    }

    /** 即使已持有摸牌張，其他玩家回合也不能提前攤牌。 */
    @Test
    fun `hand reveal outside own turn is rejected without changing the game`() = runTest {
        val playerId = Uuid.random()
        val table = tableState(playerId, drawn = true).copy(currentPlayerIndex = 1)
        val fixtures = Fixtures(table)
        fixtures.install()

        assertIs<Outcome.Error<*>>(fixtures.execute(playerId))
        assertEquals(table, fixtures.store.snapshot().games.getValue(table.id).tableState)
    }

    /** 尚未摸牌時攤牌應被拒絕，且不得修改桌況。 */
    @Test
    fun `hand reveal before drawing is rejected without changing the game`() = runTest {
        val playerId = Uuid.random()
        val table = tableState(playerId, drawn = false)
        val fixtures = Fixtures(table)
        fixtures.install()

        assertIs<Outcome.Error<*>>(fixtures.execute(playerId))
        assertEquals(table, fixtures.store.snapshot().games.getValue(table.id).tableState)
    }

    /** 非該對局玩家不得提交攤牌命令。 */
    @Test
    fun `hand reveal from a different player is rejected`() = runTest {
        val playerId = Uuid.random()
        val table = tableState(playerId, drawn = true)
        val fixtures = Fixtures(table)
        fixtures.install()

        assertIs<Outcome.Error<*>>(fixtures.execute(Uuid.random()))
        assertEquals(table, fixtures.store.snapshot().games.getValue(table.id).tableState)
    }

    /** 攤牌後換手牌與摸牌位時，觀察者快照只保留最新的公開牌。 */
    @Test
    fun `revealed hand snapshot follows successive hand replacements`() = runTest {
        val playerId = Uuid.random()
        val table = tableState(playerId, drawn = true)
        val fixtures = Fixtures(table)
        fixtures.install()

        assertIs<Outcome.Success<*>>(fixtures.execute(playerId))
        val observerId = table.players.first { it.id != playerId }.id
        val firstStanding = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2))
        val firstDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        fixtures.replaceHand(playerId, Hand(tiles = listOf(firstStanding), lastDrawn = firstDrawn))
        val firstSnapshot = fixtures.snapshotFor(observerId)
        assertEquals(setOf(firstStanding.id, firstDrawn.id), firstSnapshot.tilePoseStates.map { it.tileId }.toSet())

        val secondStanding = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 3))
        val secondDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 3))
        fixtures.replaceHand(playerId, Hand(tiles = listOf(secondStanding), lastDrawn = secondDrawn))
        val secondSnapshot = fixtures.snapshotFor(observerId)
        assertEquals(setOf(secondStanding.id, secondDrawn.id), secondSnapshot.tilePoseStates.map { it.tileId }.toSet())
    }

    /**
     * 建立供攤牌命令測試使用的桌況。
     *
     * @param playerId 要測試攤牌的玩家識別碼。
     * @param drawn 是否為該玩家建立已摸牌的回合狀態。
     * @return 包含測試玩家與另一位玩家的桌況。
     */
    private fun tableState(playerId: Uuid, drawn: Boolean): TableState {
        val standing = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            components = ComponentSet.EMPTY,
            hand = Hand(tiles = listOf(standing), lastDrawn = lastDrawn.takeIf { drawn }),
        )
        val other = FakeMahjongPlayerFactory.create()
        val table = FakeTableStateFactory.create(
            config = ExamplePairingRuleConfig(),
            players = listOf(player, other),
        )
        return GameInitializationResult(table, null, null)
            .initializeRuleComponents(ExamplePairingIds.RULE_MODULE, exampleComponentRegistry(), random = testRoundRandomness().components).tableState
    }

    /**
     * 建立攤牌 handler 測試所需的權威儲存、模組與命令執行環境。
     *
     * @property tableState 測試要安裝進權威儲存的初始桌況。
     */
    private class Fixtures(
        private val tableState: TableState,
    ) {
        /** 測試用的權威狀態儲存。 */
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, maxPendingHistoryEvents = 100)

        /** 測試使用的麻將規則模組 registry。 */
        private val moduleRegistry = MahjongModuleRegistryImpl()

        /** 本測試的正式組件登記。 */
        private val componentRegistry = exampleComponentRegistry()

        /** 使用本範例正式組件驗證器的權威對局 repository。 */
        private val repository = GameRepositoryImpl(store, AuthoritativeComponentValidatorImpl(moduleRegistry, componentRegistry))

        /** 提供規則與命令註冊的範例擴充。 */
        private val extension = ExampleMahjongExtension()

        /** 測試用的觀察者快照儲存。 */
        private val snapshotRepository = FakeGameSnapshotRepository()

        /** 將權威狀態同步為觀察者可見快照的服務。 */
        private val snapshotSynchronizer: GameSnapshotSynchronizer

        /** 執行範例擴充命令的測試 executor。 */
        private val executor: ExtensionGameCommandExecutor

        init {
            extension.registerRuleModules(moduleRegistry)
            moduleRegistry.freeze()
            snapshotSynchronizer = GameSnapshotSynchronizer(
                gameRepository = repository,
                snapshotRepository = snapshotRepository,
                visibilityPolicy = GameVisibilityPolicyImpl(moduleRegistry, componentRegistry = componentRegistry),
            )
            val handlers = ExtensionGameCommandExecutorRegistry().also {
                extension.registerGameCommandHandlers(it)
                it.freeze()
            }
            val handSort = HandSortPreferenceStore()
            val postActionResolvers = PostActionExhaustiveDrawResolverRegistry().apply { freeze() }
            val events = FakeGameEventPublisher()
            val presentations = FakeGamePresentationPublisher()
            executor = ExtensionGameCommandExecutor(
                registry = handlers,
                context = ExtensionGameCommandContext(
                    gameRepository = repository,
                    moduleRegistry = moduleRegistry,
                    snapshotSynchronizer = snapshotSynchronizer,
                    handSortPreferenceStore = handSort,
                    postActionExhaustiveDrawResolverRegistry = postActionResolvers,
                    eventPublisher = events,
                    presentationPublisher = presentations,
                    declareAndDiscardUseCase = DeclareAndDiscardUseCase(
                        gameRepository = repository,
                        moduleRegistry = moduleRegistry,
                        snapshotSynchronizer = snapshotSynchronizer,
                        handSortPreferenceStore = handSort,
                        postActionExhaustiveDrawResolverRegistry = postActionResolvers,
                        eventPublisher = events,
                        presentationPublisher = presentations,

                        componentRegistry = componentRegistry,
                    ),

                    componentRegistry = componentRegistry,
                ),
            )
        }

        /** 將初始桌況安裝到權威狀態儲存。 */
        suspend fun install() {
            store.update { state ->
                AuthoritativeStateUpdate(
                    state = state.copy(games = state.games + (tableState.id to Game(tableState, GameFlowConfig()))),
                    result = Unit,
                )
            }
        }

        /** 以 repository 模擬摸打後的最新手牌，保留規則的攤牌狀態。 */
        suspend fun replaceHand(playerId: Uuid, hand: Hand) {
            repository.updateGame(tableState.id) { game ->
                val current = checkNotNull(game)
                current.copy(
                    tableState = current.tableState.copy(
                        players = current.tableState.players.map { player ->
                            if (player.id == playerId) player.copy(hand = hand) else player
                        },
                    ),
                ) to Unit
            }
        }

        /** 透過正式 visibility policy 產生指定觀察者的最新快照。 */
        suspend fun snapshotFor(observerId: Uuid) = snapshotSynchronizer.sync(tableState.id, observerId).let {
            check(it)
            snapshotRepository.getSnapshot(tableState.id, observerId)!!
        }

        /**
         * 以指定玩家送出一次攤牌命令。
         *
         * @param playerId 送出命令的玩家識別碼。
         * @return 命令執行結果。
         */
        suspend fun execute(playerId: Uuid): Outcome<Unit, GameError> = executor.execute(tableState.id, playerId, ExamplePairingRevealHandCommand)
    }
}
