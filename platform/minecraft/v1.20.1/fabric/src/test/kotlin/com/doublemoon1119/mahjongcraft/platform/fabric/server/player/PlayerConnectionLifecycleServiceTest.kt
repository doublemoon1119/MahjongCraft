package com.doublemoon1119.mahjongcraft.platform.fabric.server.player

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.LeaveRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.SyncRoomSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.GameSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.RoomSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.DisconnectedPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** [PlayerConnectionLifecycleService] 的斷線政策、重連取消與重連快照補送測試。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnectionLifecycleServiceTest {
    /** `KEEP_SEAT` 不得移除等待室玩家。 */
    @Test
    fun `test keep seat policy preserves waiting room membership`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.KEEP_SEAT)

        fixture.service.onDisconnected(fixture.playerId)
        advanceUntilIdle()

        assertNotNull(fixture.roomRepository.getRoom(fixture.tableId))
        assertEquals(fixture.tableId, fixture.memberships.getTableId(fixture.playerId))
    }

    /** `LEAVE_IMMEDIATELY` 應在玩家斷線後立即呼叫等待室離開流程。 */
    @Test
    fun `test immediate policy removes disconnected waiting room player`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY)

        fixture.service.onDisconnected(fixture.playerId)
        advanceUntilIdle()

        assertNull(fixture.memberships.getTableId(fixture.playerId))
        assertNull(fixture.roomRepository.getRoom(fixture.tableId)?.playerIds?.find { it == fixture.playerId })
    }

    /** `LEAVE_AFTER_TIMEOUT` 只在完整寬限時間到期後移除玩家。 */
    @Test
    fun `test timeout policy removes player after grace period`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT, timeoutSeconds = 5)

        fixture.service.onDisconnected(fixture.playerId)
        runCurrent()
        advanceTimeBy(4_999.milliseconds)
        runCurrent()
        assertEquals(fixture.tableId, fixture.memberships.getTableId(fixture.playerId))

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertNull(fixture.memberships.getTableId(fixture.playerId))
    }

    /** 玩家在寬限時間內重連時應取消逾時離開。 */
    @Test
    fun `test reconnect cancels pending timeout leave`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT, timeoutSeconds = 5)

        fixture.service.onDisconnected(fixture.playerId)
        runCurrent()
        advanceTimeBy(2.seconds)
        fixture.service.onConnected(fixture.playerId)
        advanceUntilIdle()

        assertEquals(fixture.tableId, fixture.memberships.getTableId(fixture.playerId))
        assertNotNull(fixture.roomRepository.getRoom(fixture.tableId))
    }

    /**
     * 客戶端主動請求時應補送一份快照——這是玩家重新登入後手牌會暫時顯示 unknown 的實際回報問題
     * （見 [PlayerConnectionLifecycleService.onSnapshotRequested] KDoc）。改成客戶端主動請求
     * （`mahjongcraft:request_snapshot`）觸發，不是連線本身觸發，見該方法 KDoc。
     */
    @Test
    fun `test snapshot request resyncs waiting room snapshot`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.KEEP_SEAT)
        assertNull(fixture.roomSnapshotRepository.getSnapshot(fixture.tableId, fixture.playerId))

        fixture.service.onSnapshotRequested(fixture.playerId)
        advanceUntilIdle()

        assertNotNull(fixture.roomSnapshotRepository.getSnapshot(fixture.tableId, fixture.playerId))
    }

    /** 對局已開始時，快照補送請求應改補送對局快照，而不是等待室快照。 */
    @Test
    fun `test snapshot request resyncs active game snapshot`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.KEEP_SEAT)
        fixture.roomRepository.removeRoom(fixture.tableId)
        fixture.gameRepository.setTableState(
            FakeTableStateFactory.create(id = fixture.tableId, players = fixture.players),
        )
        assertNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.playerId))

        fixture.service.onSnapshotRequested(fixture.playerId)
        advanceUntilIdle()

        assertNotNull(fixture.gameSnapshotRepository.getSnapshot(fixture.tableId, fixture.playerId))
    }

    /** 進行中的 Game 不受斷線離開政策影響。 */
    @Test
    fun `test active game always keeps disconnected player membership`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY)
        fixture.roomRepository.removeRoom(fixture.tableId)
        fixture.gameRepository.setTableState(FakeTableStateFactory.create(id = fixture.tableId))

        fixture.service.onDisconnected(fixture.playerId)
        advanceUntilIdle()

        assertEquals(fixture.tableId, fixture.memberships.getTableId(fixture.playerId))
    }

    /** 沒有 membership 的玩家斷線時不得改動任何房間。 */
    @Test
    fun `test disconnected player without membership is ignored`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY)
        fixture.memberships.release(fixture.playerId, fixture.tableId)

        fixture.service.onDisconnected(fixture.playerId)
        advanceUntilIdle()

        assertNotNull(fixture.roomRepository.getRoom(fixture.tableId))
    }

    /** 後續斷線事件應讀取熱重載後的政策。 */
    @Test
    fun `test subsequent disconnect uses replaced config`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.KEEP_SEAT)
        fixture.configState.replace(
            MinecraftServerConfig(disconnectedPlayerPolicy = DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY),
        )

        fixture.service.onDisconnected(fixture.playerId)
        advanceUntilIdle()

        assertNull(fixture.memberships.getTableId(fixture.playerId))
    }

    /** 已排程工作應保留斷線當下的 timeout，不受後續設定替換影響。 */
    @Test
    fun `test pending timeout keeps scheduling config snapshot`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT, timeoutSeconds = 5)
        fixture.service.onDisconnected(fixture.playerId)
        runCurrent()
        fixture.configState.replace(
            MinecraftServerConfig(
                disconnectedPlayerPolicy = DisconnectedPlayerPolicy.KEEP_SEAT,
                disconnectedPlayerTimeoutSeconds = 300,
            ),
        )

        advanceTimeBy(5.seconds)
        runCurrent()

        assertNull(fixture.memberships.getTableId(fixture.playerId))
    }

    /** 建立使用目前測試 scheduler 的斷線政策測試資料。 */
    private suspend fun kotlinx.coroutines.test.TestScope.createFixture(
        policy: DisconnectedPlayerPolicy,
        timeoutSeconds: Long = 300,
    ): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = createTestAppCoroutineScope(TestCoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        val store = AuthoritativeStateStore()
        val roomRepository = RoomRepositoryImpl(store)
        val gameRepository = GameRepositoryImpl(store)
        val memberships = PlayerMembershipRepositoryImpl()
        val tableId = Uuid.random()
        val hostId = Uuid.random()
        val playerId = Uuid.random()
        val players = listOf(
            FakeMahjongPlayerFactory.create(id = hostId),
            FakeMahjongPlayerFactory.create(id = playerId),
        )
        roomRepository.setRoom(
            Room(
                id = tableId,
                hostId = hostId,
                gameConfig = GameConfig(FakeMahjongRuleConfig()),
                playerIds = listOf(hostId, playerId),
            ),
        )
        memberships.claim(playerId, tableId)
        val roomSnapshotRepository = FakeRoomSnapshotRepository()
        val gameSnapshotRepository = FakeGameSnapshotRepository()
        val leaveRoom = LeaveRoomUseCase(
            roomRepository,
            memberships,
            roomSnapshotRepository,
            FakeRoomEventPublisher(),
        )
        val configState = MinecraftServerConfigState(
            MinecraftServerConfig(
                disconnectedPlayerPolicy = policy,
                disconnectedPlayerTimeoutSeconds = timeoutSeconds,
            ),
        )
        val networkRegistries = DefaultNetworkDtoRegistries()
        val service = PlayerConnectionLifecycleService(
            scope,
            configState,
            memberships,
            roomRepository,
            gameRepository,
            leaveRoom,
            SyncRoomSnapshotUseCase(roomRepository, roomSnapshotRepository),
            SyncGameSnapshotUseCase(GameSnapshotSynchronizer(gameRepository, gameSnapshotRepository, GameVisibilityPolicyImpl())),
            RoomSnapshotSender(roomSnapshotRepository, FabricServerHolder(), Json, networkRegistries),
            GameSnapshotSender(gameSnapshotRepository, FabricServerHolder(), Json, networkRegistries),
        )
        return Fixture(
            service,
            configState,
            roomRepository,
            gameRepository,
            memberships,
            roomSnapshotRepository,
            gameSnapshotRepository,
            tableId,
            playerId,
            players,
        )
    }

    /**
     * 測試中共用的斷線服務與權威 repository。
     *
     * @property service 受測連線生命週期服務。
     * @property configState 可替換的有效設定 state。
     * @property roomRepository Room repository。
     * @property gameRepository Game repository。
     * @property memberships 玩家唯一桌子歸屬 repository。
     * @property roomSnapshotRepository 房間快照 read-side repository，用來驗證重連是否補送快照。
     * @property gameSnapshotRepository 對局快照 read-side repository，用來驗證重連是否補送快照。
     * @property tableId 測試麻將桌 UUID。
     * @property playerId 測試玩家 UUID。
     * @property players 測試房主與測試玩家組成的固定玩家清單，供對局測試建立 [FakeTableStateFactory] 用。
     */
    private data class Fixture(
        val service: PlayerConnectionLifecycleService,
        val configState: MinecraftServerConfigState,
        val roomRepository: RoomRepositoryImpl,
        val gameRepository: GameRepositoryImpl,
        val memberships: PlayerMembershipRepositoryImpl,
        val roomSnapshotRepository: FakeRoomSnapshotRepository,
        val gameSnapshotRepository: FakeGameSnapshotRepository,
        val tableId: Uuid,
        val playerId: Uuid,
        val players: List<MahjongPlayer>,
    )
}
