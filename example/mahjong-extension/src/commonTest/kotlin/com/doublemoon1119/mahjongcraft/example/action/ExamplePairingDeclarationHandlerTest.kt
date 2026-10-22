package com.doublemoon1119.mahjongcraft.example.action

import com.doublemoon1119.mahjongcraft.example.ExampleMahjongExtension
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingPlayerState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
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
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** [ExamplePairingDeclarationHandler] 的權威驗證測試。 */
class ExamplePairingDeclarationHandlerTest {
    /** 成功宣告後再次宣告應被拒絕，且不得新增歷史動作。 */
    @Test
    fun `declaration is accepted once and duplicate is rejected without another history event`() = runTest {
        val playerId = Uuid.random()
        val table = tableState(playerId, drawn = true)
        val fixtures = Fixtures(table)
        fixtures.install()

        assertIs<Outcome.Success<*>>(fixtures.execute(playerId))
        val afterFirst = fixtures.store.snapshot()
        val actionCount = afterFirst.games.getValue(table.id).tableState.players
            .first { it.id == playerId }.actionHistory.size
        val historyCount = afterFirst.historyRecordingState.pendingEvents.size

        assertIs<Outcome.Error<*>>(fixtures.execute(playerId))
        val afterDuplicate = fixtures.store.snapshot()
        assertEquals(actionCount, afterDuplicate.games.getValue(table.id).tableState.players.first { it.id == playerId }.actionHistory.size)
        assertEquals(historyCount, afterDuplicate.historyRecordingState.pendingEvents.size)
    }

    /** 尚未摸牌時宣告應被拒絕，且不得修改桌況。 */
    @Test
    fun `declaration before drawing is rejected without changing the game`() = runTest {
        val playerId = Uuid.random()
        val table = tableState(playerId, drawn = false)
        val fixtures = Fixtures(table)
        fixtures.install()

        assertIs<Outcome.Error<*>>(fixtures.execute(playerId))
        assertEquals(table, fixtures.store.snapshot().games.getValue(table.id).tableState)
    }

    /** 非該對局玩家不得提交宣告命令。 */
    @Test
    fun `declaration from a different player is rejected`() = runTest {
        val playerId = Uuid.random()
        val table = tableState(playerId, drawn = true)
        val fixtures = Fixtures(table)
        fixtures.install()

        assertIs<Outcome.Error<*>>(fixtures.execute(Uuid.random()))
        assertEquals(table, fixtures.store.snapshot().games.getValue(table.id).tableState)
    }

    /**
     * 建立供宣告命令測試使用的桌況。
     *
     * @param playerId 要測試宣告的玩家識別碼。
     * @param drawn 是否為該玩家建立已摸牌的回合狀態。
     * @return 包含測試玩家與另一位玩家的桌況。
     */
    private fun tableState(playerId: Uuid, drawn: Boolean): TableState {
        val standing = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            playerRuleState = ExamplePairingPlayerState(),
            hand = Hand(tiles = listOf(standing), lastDrawn = lastDrawn.takeIf { drawn }),
        )
        val other = FakeMahjongPlayerFactory.create()
        return FakeTableStateFactory.create(
            config = ExamplePairingRuleConfig(),
            players = listOf(player, other),
        )
    }

    /**
     * 建立宣告 handler 測試所需的權威儲存、模組與命令執行環境。
     *
     * @property tableState 測試要安裝進權威儲存的初始桌況。
     */
    private class Fixtures(
        private val tableState: TableState,
    ) {
        /** 測試用的權威狀態儲存。 */
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, maxPendingHistoryEvents = 100)

        /** 連接測試權威狀態儲存的遊戲 repository。 */
        private val repository = GameRepositoryImpl(store)

        /** 測試使用的麻將規則模組 registry。 */
        private val moduleRegistry = MahjongModuleRegistryImpl()

        /** 提供規則與命令註冊的範例擴充。 */
        private val extension = ExampleMahjongExtension()

        /** 將權威狀態同步為觀察者可見快照的服務。 */
        private val snapshotSynchronizer: GameSnapshotSynchronizer

        /** 執行範例擴充命令的測試 executor。 */
        private val executor: ExtensionGameCommandExecutor

        init {
            extension.registerRuleModules(moduleRegistry)
            moduleRegistry.freeze()
            snapshotSynchronizer = GameSnapshotSynchronizer(
                repository,
                FakeGameSnapshotRepository(),
                GameVisibilityPolicyImpl(moduleRegistry),
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
                handlers,
                ExtensionGameCommandContext(
                    repository,
                    moduleRegistry,
                    snapshotSynchronizer,
                    handSort,
                    postActionResolvers,
                    events,
                    presentations,
                    DeclareAndDiscardUseCase(
                        repository,
                        moduleRegistry,
                        snapshotSynchronizer,
                        handSort,
                        postActionResolvers,
                        events,
                        presentations,
                    ),
                ),
            )
        }

        /** 將初始桌況安裝到權威狀態儲存。 */
        suspend fun install() {
            store.update { state ->
                AuthoritativeStateUpdate(
                    state.copy(games = state.games + (tableState.id to Game(tableState, GameFlowConfig()))),
                    Unit,
                )
            }
        }

        /**
         * 以指定玩家送出一次宣告命令。
         *
         * @param playerId 送出命令的玩家識別碼。
         * @return 命令執行結果。
         */
        suspend fun execute(playerId: Uuid): Outcome<Unit, GameError> = executor.execute(tableState.id, playerId, ExamplePairingDeclarationCommand)
    }
}
