package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScopeImpl
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverAudienceSource
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.HandReadinessVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.observer.ObserverSnapshotBroadcaster
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.OrphanedTablePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證桌況交付在來源移除、重建與伺服器切換期間不會送出過期內容。 */
@OptIn(ExperimentalCoroutinesApi::class)
class FabricTableStateDeliveryTest {
    /** 有效桌子可在指定主執行緒調度器完成一次送出。 */
    @Test
    fun `valid delivery runs on the main dispatcher`() = runTest {
        val fixture = Fixture(this)
        val ticket = fixture.capture()
        val sent = mutableListOf<String>()

        val delivered = prepareAndSend(fixture, ticket, { "current" }) { sent += it }
        advanceUntilIdle()

        assertTrue(delivered)
        assertEquals(listOf("current"), sent)
    }

    /** 準備內容暫停期間來源被移除時，不送出舊快照。 */
    @Test
    fun `removed entry while preparing does not send old snapshot`() = runTest {
        val fixture = Fixture(this)
        val ticket = fixture.capture()
        val sent = mutableListOf<String>()
        var releasePreparation = false

        val job = async {
            fixture.delivery.prepareAndSend(ticket, {
                while (!releasePreparation) yield()
                "old"
            }) { sent += it }
        }
        fixture.environment.removeTable(fixture.tableId)
        releasePreparation = true
        advanceUntilIdle()
        job.await()

        assertTrue(sent.isEmpty())
    }

    /** 捕捉後桌子已刪除時，預設排程來源也不得送出內容。 */
    @Test
    fun `deleted source after capture skips queued delivery`() = runTest {
        val fixture = Fixture(this)
        val ticket = fixture.capture()
        fixture.environment.removeTable(fixture.tableId)
        val sent = mutableListOf<String>()

        val delivered = prepareAndSend(fixture, ticket, { "old" }) { sent += it }
        advanceUntilIdle()

        assertFalse(delivered)
        assertTrue(sent.isEmpty())
    }

    /** 位置已消失但權威桌況仍存在時，不得送出桌子移除通知。 */
    @Test
    fun `location missing but authoritative state still exists rejects removal`() = runTest {
        val fixture = Fixture(this)
        val ticket = fixture.capture()
        assertTrue(fixture.delivery.beginRemoval(ticket))
        fixture.environment.locations.remove(fixture.tableId, ticket.entry.revision)
        fixture.environment.sourceExists = true
        val sent = mutableListOf<Unit>()

        assertFalse(sendRemoval(fixture, ticket) { sent += Unit })
        advanceUntilIdle()
        assertTrue(sent.isEmpty())
    }

    /** 移除與交付共用同一序列時，移除完成後不會再送出舊內容。 */
    @Test
    fun `removal sequence never sends after source is removed`() = runTest {
        val fixture = Fixture(this)
        val ticket = fixture.capture()
        assertTrue(fixture.delivery.beginRemoval(ticket))
        fixture.environment.removeTable(fixture.tableId)
        val sent = mutableListOf(Unit)

        val delivered = sendRemoval(fixture, ticket) { sent += Unit }
        advanceUntilIdle()

        assertTrue(delivered)
        assertEquals(2, sent.size)
        assertFalse(fixture.delivery.isCurrent(ticket))
    }

    /** 同桌以相同 revision 建立新來源時，舊移除票據不能刪除或阻擋新來源。 */
    @Test
    fun `new entry with reused revision rejects old removal`() = runTest {
        val fixture = Fixture(this)
        val oldTicket = fixture.capture()
        assertTrue(fixture.delivery.beginRemoval(oldTicket))
        fixture.environment.removeTable(fixture.tableId)
        fixture.environment.locations.clear()
        fixture.environment.addTable(fixture.tableId)

        assertFalse(fixture.delivery.isCurrent(oldTicket))
        val newTicket = fixture.capture()
        val sent = mutableListOf<String>()
        assertTrue(prepareAndSend(fixture, newTicket, { "new" }) { sent += it })
        advanceUntilIdle()

        assertEquals(listOf("new"), sent)
        assertFalse(sendRemoval(fixture, oldTicket) {})
    }

    /** 伺服器切換後，舊 session 的票據不能再交付。 */
    @Test
    fun `old ticket is rejected after session changes`() = runTest {
        val fixture = Fixture(this)
        val ticket = fixture.capture()
        fixture.environment.sessionId = Uuid.random()
        fixture.environment.removeTable(fixture.tableId)

        assertFalse(fixture.delivery.beginRemoval(ticket))
        assertFalse(prepareAndSend(fixture, ticket, { "old" }) {})
        advanceUntilIdle()
    }

    /** 送出失敗或取消後，交付鎖仍可讓另一張桌子繼續送出。 */
    @Test
    fun `failure and cancellation release the delivery mutex`() = runTest {
        val fixture = Fixture(this)
        val first = fixture.capture()
        val secondId = Uuid.random()
        fixture.environment.addTable(secondId)
        val second = fixture.delivery.capture(secondId) ?: error("second ticket missing")

        var failed = false
        try {
            prepareAndSend(fixture, first, { "first" }) { error("send failed") }
        } catch (_: IllegalStateException) {
            // 預期第一次送出失敗；鎖應在例外後釋放。
            failed = true
        }
        assertTrue(failed)
        assertTrue(prepareAndSend(fixture, second, { "second" }) {})

        var cancelled = false
        try {
            prepareAndSend(fixture, first, { "first" }) { throw CancellationException("cancel") }
        } catch (_: CancellationException) {
            // 預期取消向外傳遞；鎖仍應可供下一筆工作使用。
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(prepareAndSend(fixture, second, { "second" }) {})
    }

    /** 舊票據完成回呼不能清除同桌後來建立的移除請求。 */
    @Test
    fun `finishing old ticket does not clear newer removal`() = runTest {
        val fixture = Fixture(this)
        val oldTicket = fixture.capture()
        assertTrue(fixture.delivery.beginRemoval(oldTicket))
        fixture.environment.removeTable(fixture.tableId)
        fixture.environment.locations.clear()
        fixture.environment.addTable(fixture.tableId)
        val newTicket = fixture.capture()
        assertTrue(fixture.delivery.beginRemoval(newTicket))

        fixture.delivery.finishRemoval(oldTicket)

        assertEquals(newTicket, fixture.delivery.removalTicket(fixture.tableId, newTicket.requestId))
    }

    /** 真實清理服務與 notifier 串接時，位置先移除仍會交付失效通知，且舊桌況不會延遲送出。 */
    @Test
    fun `cleanup delivers removal after location index disappears`() = runTest {
        val dispatchers = TestDispatchers(this)
        val environment = FakeTableStateDeliveryEnvironment()
        val tableId = Uuid.random()
        val entry = environment.addTable(tableId)
        environment.sourceExists = false
        val store = AuthoritativeStateStore()
        val registry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val trackingObserverId = Uuid.random()
        environment.trackingObserverIds = setOf(trackingObserverId)
        val audience = FixedObserverAudienceSource(tableId, environment, setOf(trackingObserverId))
        val sender = RecordingObserverSnapshotSender()
        val broadcaster = ObserverSnapshotBroadcaster(
            store = store,
            visibilityPolicy = GameVisibilityPolicyImpl(registry),
            handReadinessVisibilityPolicy = HandReadinessVisibilityPolicy(
                moduleRegistry = registry,
                visibilityPolicy = GameVisibilityPolicyImpl(registry),
            ),
            audienceSource = audience,
            sender = sender,
        )
        val scope = AppCoroutineScopeImpl(dispatchers)
        val delivery = FabricTableStateDelivery(dispatchers, environment)
        sender.delivery = delivery
        val notifier = FabricTableRemovalNotifier(delivery, environment, broadcaster, scope, dispatchers)
        val service = OrphanedTableCleanupService(
            store = store,
            memberships = PlayerMembershipRepositoryImpl(),
            roomSnapshots = RoomSnapshotRepositoryImpl(),
            gameSnapshots = GameSnapshotRepositoryImpl(),
            locations = environment.locations,
            configState = MinecraftServerConfigState(
                initialConfig = MinecraftServerConfig(orphanedTablePolicy = OrphanedTablePolicy.REMOVE_ALL),
            ),
            removalNotifier = notifier,
        )

        broadcaster.broadcast()
        val oldTicket = delivery.capture(tableId) ?: error("table ticket missing")
        val resumePreparation = CompletableDeferred<Unit>()
        var staleSnapshotSent = false
        val delayedSnapshot = async {
            delivery.prepareAndSend(
                ticket = oldTicket,
                prepare = {
                    resumePreparation.await()
                    Unit
                },
                send = { staleSnapshotSent = true },
            )
        }
        runCurrent()
        val result = service.cleanupMissing(tableId, entry.revision)
        resumePreparation.complete(Unit)
        advanceUntilIdle()
        scope.cancel()

        assertFalse(delayedSnapshot.await())
        assertFalse(staleSnapshotSent)
        assertEquals(OrphanedTableCleanupResult.REMOVED_LOCATION, result)
        assertNull(environment.locations.get(tableId))
        assertEquals(setOf(trackingObserverId), sender.invalidated.map { it.second }.toSet())
    }

    /**
     * 測試用桌子識別碼與一張位置索引條目。
     *
     * @param scope 控制交付排程的測試作用域。
     */
    private class Fixture(scope: TestScope) {
        /** 測試桌子的識別碼。 */
        val tableId = Uuid.random()

        /** 可控制來源生命週期的測試環境。 */
        val environment = FakeTableStateDeliveryEnvironment(TableLocationRegistry())

        /** 使用測試主執行緒調度器的桌況交付器。 */
        val delivery = FabricTableStateDelivery(TestDispatchers(scope), environment)

        init {
            environment.addTable(tableId)
        }

        /** 取得目前桌子的來源票據。 */
        fun capture(): FabricTableStateTicket = delivery.capture(tableId) ?: error("table ticket missing")
    }

    /**
     * 只在桌子位置仍存在時回傳觀察者，模擬移除索引後查不到接收者。
     *
     * @property tableId 測試桌子的識別碼。
     * @property environment 提供目前位置的測試環境。
     * @property observerIds 桌子存在時的觀察者集合。
     */
    private class FixedObserverAudienceSource(
        private val tableId: Uuid,
        private val environment: FakeTableStateDeliveryEnvironment,
        private val observerIds: Set<Uuid>,
    ) : ObserverAudienceSource {
        override suspend fun observersById(): Map<Uuid, Set<Uuid>> = if (environment.locations.get(tableId) == null) emptyMap() else mapOf(tableId to observerIds)
    }

    /** 記錄真實 broadcaster 交給平台 sender 的失效通知。 */
    private class RecordingObserverSnapshotSender : ObserverSnapshotSender {
        /** 已收到的場地、觀察者與失效請求識別碼。 */
        val invalidated = mutableListOf<Triple<Uuid, Uuid, Uuid>>()

        /** 將失效通知交給桌況交付器的 gateway。 */
        lateinit var delivery: FabricTableStateDelivery

        override suspend fun send(
            id: Uuid,
            observerId: Uuid,
            snapshot: ObserverSnapshot?,
        ) = Unit

        override suspend fun invalidateVenue(
            id: Uuid,
            observerId: Uuid,
            requestId: Uuid,
        ) {
            val ticket = delivery.removalTicket(id, requestId) ?: return
            delivery.sendRemoval(ticket) { invalidated += Triple(id, observerId, requestId) }
        }
    }

    /** 在測試主執行緒上執行一次桌況準備與送出。 */
    private suspend fun <T : Any> TestScope.prepareAndSend(
        fixture: Fixture,
        ticket: FabricTableStateTicket,
        prepare: suspend () -> T?,
        send: (T) -> Unit,
    ): Boolean = supervisorScope {
        val deferred = async { fixture.delivery.prepareAndSend(ticket, prepare, send) }
        advanceUntilIdle()
        deferred.await()
    }

    /** 在測試主執行緒上執行一次桌子移除通知。 */
    private suspend fun TestScope.sendRemoval(
        fixture: Fixture,
        ticket: FabricTableStateTicket,
        send: () -> Unit,
    ): Boolean = supervisorScope {
        val deferred = async { fixture.delivery.sendRemoval(ticket, send) }
        advanceUntilIdle()
        deferred.await()
    }

    /**
     * 使用 runTest 控制主執行緒與背景排程的測試調度器。
     *
     * @param scope 提供共用排程器的測試作用域。
     */
    private class TestDispatchers(scope: TestScope) : CoroutineDispatchers {
        override val default: CoroutineDispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        override val io: CoroutineDispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        override val main: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler)
        override val aiDecision: CoroutineDispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        override val aiDecisionParallelism: Int = 1
    }
}
