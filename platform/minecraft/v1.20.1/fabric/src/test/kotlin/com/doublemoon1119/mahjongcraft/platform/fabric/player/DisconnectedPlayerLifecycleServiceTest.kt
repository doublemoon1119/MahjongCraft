package com.doublemoon1119.mahjongcraft.platform.fabric.player

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.LeaveRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.DisconnectedPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [DisconnectedPlayerLifecycleService] 的斷線政策與重連取消測試。 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisconnectedPlayerLifecycleServiceTest {
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
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(fixture.tableId, fixture.memberships.getTableId(fixture.playerId))

        advanceTimeBy(1)
        runCurrent()
        assertNull(fixture.memberships.getTableId(fixture.playerId))
    }

    /** 玩家在寬限時間內重連時應取消逾時離開。 */
    @Test
    fun `test reconnect cancels pending timeout leave`() = runTest {
        val fixture = createFixture(DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT, timeoutSeconds = 5)

        fixture.service.onDisconnected(fixture.playerId)
        runCurrent()
        advanceTimeBy(2_000)
        fixture.service.onConnected(fixture.playerId)
        advanceUntilIdle()

        assertEquals(fixture.tableId, fixture.memberships.getTableId(fixture.playerId))
        assertNotNull(fixture.roomRepository.getRoom(fixture.tableId))
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
        roomRepository.setRoom(
            Room(
                id = tableId,
                hostId = hostId,
                gameConfig = GameConfig(FakeMahjongRuleConfig()),
                playerIds = setOf(hostId, playerId),
            ),
        )
        memberships.claim(playerId, tableId)
        val leaveRoom = LeaveRoomUseCase(
            roomRepository,
            memberships,
            FakeRoomSnapshotRepository(),
            FakeRoomEventPublisher(),
        )
        val service = DisconnectedPlayerLifecycleService(
            scope,
            MinecraftServerConfig(
                disconnectedPlayerPolicy = policy,
                disconnectedPlayerTimeoutSeconds = timeoutSeconds,
            ),
            memberships,
            roomRepository,
            gameRepository,
            leaveRoom,
        )
        return Fixture(service, roomRepository, gameRepository, memberships, tableId, playerId)
    }

    /** 測試中共用的斷線服務與權威 repository。 */
    private data class Fixture(
        val service: DisconnectedPlayerLifecycleService,
        val roomRepository: RoomRepositoryImpl,
        val gameRepository: GameRepositoryImpl,
        val memberships: PlayerMembershipRepositoryImpl,
        val tableId: Uuid,
        val playerId: Uuid,
    )
}
