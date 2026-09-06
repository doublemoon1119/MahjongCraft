package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.GameSnapshotSender
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * [SpectatorObservationService] 的單元測試類別。
 *
 * 驗證「非在場玩家開始／停止看到某桌」時，依 [SpectatingPolicy] 正確補送或清除快照，且完全不影響
 * 在場玩家——在場玩家已經有既有路徑（右鍵互動桌子）負責同步，這裡必須明確排除他們。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpectatorObservationServiceTest {
    /** 旁觀開放時，非在場玩家開始觀察應該補送一份過濾後快照。 */
    @Test
    fun `test started observing syncs snapshot for spectator when spectating enabled`() = runTest {
        val fixture = createFixture()
        assertNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.spectatorId))

        fixture.service.onStartedObserving(fixture.spectatorId, fixture.tableId)
        advanceUntilIdle()

        assertNotNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.spectatorId))
    }

    /** 旁觀關閉時，非在場玩家開始觀察不應該取得任何快照。 */
    @Test
    fun `test started observing does nothing when spectating disabled`() = runTest {
        val fixture = createFixture()
        fixture.gameRepository.updateGame(fixture.tableId) { game ->
            checkNotNull(game).copy(flowConfig = GameFlowConfig(spectatingPolicy = SpectatingPolicy.DISABLED)) to Unit
        }

        fixture.service.onStartedObserving(fixture.spectatorId, fixture.tableId)
        advanceUntilIdle()

        assertNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.spectatorId))
    }

    /** 在場玩家不受這個機制影響——他們的快照交給既有的互動流程負責，這裡必須完全跳過。 */
    @Test
    fun `test started observing skips seated players`() = runTest {
        val fixture = createFixture()
        assertNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.seatedPlayerId))

        fixture.service.onStartedObserving(fixture.seatedPlayerId, fixture.tableId)
        advanceUntilIdle()

        assertNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.seatedPlayerId))
    }

    /** 停止觀察時應該清除旁觀者的快照。 */
    @Test
    fun `test stopped observing removes spectator snapshot`() = runTest {
        val fixture = createFixture()
        fixture.service.onStartedObserving(fixture.spectatorId, fixture.tableId)
        advanceUntilIdle()
        assertNotNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.spectatorId))

        fixture.service.onStoppedObserving(fixture.spectatorId, fixture.tableId)
        advanceUntilIdle()

        assertNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.spectatorId))
    }

    /** 在場玩家停止被追蹤時不得清除他們的快照——重連仍然需要它。 */
    @Test
    fun `test stopped observing keeps seated player snapshot`() = runTest {
        val fixture = createFixture()
        val tableState = checkNotNull(fixture.gameRepository.getGame(fixture.tableId)).tableState
        fixture.gameSnapshotRepository.setSnapshot(fixture.seatedPlayerId, tableState.toSnapshot(emptySet()))

        fixture.service.onStoppedObserving(fixture.seatedPlayerId, fixture.tableId)
        advanceUntilIdle()

        assertNotNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.seatedPlayerId))
    }

    /** 建立使用目前測試 scheduler 的旁觀觀察服務測試資料。 */
    private suspend fun TestScope.createFixture(): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = createTestAppCoroutineScope(TestCoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        val store = AuthoritativeStateStore()
        val gameRepository = GameRepositoryImpl(store)
        val tableId = Uuid.random()
        val seatedPlayerId = Uuid.random()
        val spectatorId = Uuid.random()
        val players = listOf(FakeMahjongPlayerFactory.create(id = seatedPlayerId))
        gameRepository.setTableState(FakeTableStateFactory.create(id = tableId, players = players))
        val gameSnapshotRepository = FakeGameSnapshotRepository()
        val networkRegistries = DefaultNetworkDtoRegistries()
        val service = SpectatorObservationService(
            scope,
            gameRepository,
            SyncGameSnapshotUseCase(GameSnapshotSynchronizer(gameRepository, gameSnapshotRepository, GameVisibilityPolicyImpl())),
            GameSnapshotSender(gameSnapshotRepository, FabricServerHolder(), Json, networkRegistries),
            gameSnapshotRepository,
        )
        return Fixture(service, gameRepository, gameSnapshotRepository, tableId, seatedPlayerId, spectatorId)
    }

    /**
     * 測試中共用的旁觀觀察服務與權威 repository。
     *
     * @property service 受測旁觀觀察服務。
     * @property gameRepository Game repository，用於安排桌況與 [GameFlowConfig]。
     * @property gameSnapshotRepository 對局快照 read-side repository，用來驗證補送/清除是否生效。
     * @property tableId 測試麻將桌 UUID。
     * @property seatedPlayerId 測試中唯一的在場玩家 UUID。
     * @property spectatorId 測試中非在場的旁觀者 UUID。
     */
    private data class Fixture(
        val service: SpectatorObservationService,
        val gameRepository: GameRepositoryImpl,
        val gameSnapshotRepository: FakeGameSnapshotRepository,
        val tableId: Uuid,
        val seatedPlayerId: Uuid,
        val spectatorId: Uuid,
    )
}
