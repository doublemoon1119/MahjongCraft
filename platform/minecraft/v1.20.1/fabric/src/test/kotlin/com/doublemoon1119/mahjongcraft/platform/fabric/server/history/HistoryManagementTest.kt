package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證管理入口的唯讀預覽、部分結果與 session 工作隔離。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryManagementTest {
    /** 預覽不刪除資料，執行會重新評估政策並回報實際用量。 */
    @Test
    fun `preview is read only and cleanup reports committed removals`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = HistoryRetentionCoordinator()
        val writer = writer(dispatcher, coordinator)
        val path = createTempDirectory("history-management-").resolve("history.sqlite")
        try {
            writer.attach(path)
            runCurrent()
            val database = SqliteHistoryDatabase.open(path)
            val id = Uuid.random().toString()
            database.recordTerminals(listOf(HistoryTerminalRecord(id, Uuid.random().toString(), 100L, false)))
            database.appendPending(PendingHistoryRecord(id, 1L, 1, 100L, 1, "龍"))
            coordinator.apply(MinecraftHistoryConfig(includeInterruptedMatches = false, retentionDays = 0).retentionPolicy())
            val before = database.measureDiskUsage()
            val preview = assertIs<HistoryManagementResult.Success<HistoryCleanupPreview>>(writer.previewCleanup()).value
            assertEquals(1L, preview.countsByReason[HistoryCleanupReason.INTERRUPTED_EXCLUDED])
            assertEquals(3L, preview.logicalBytes)
            assertEquals(before, database.measureDiskUsage())
            assertTrue(database.readTombstones().isEmpty())
            assertEquals(1, database.readPending(id).size)

            val result = assertIs<HistoryManagementResult.Success<HistoryCleanupReport>>(writer.runCleanup()).value
            assertEquals(1L, result.removedMatches)
            assertTrue(result.completed)
            assertNotNull(result.diskBefore)
            assertNotNull(result.diskAfter)
            assertEquals(setOf(id), database.readTombstones())
            assertEquals(0L, assertIs<HistoryManagementResult.Success<HistoryStorageSnapshot>>(writer.storage()).value.partialMatchCount)
        } finally {
            writer.detach()
        }
    }

    /** SQL 已提交後的對帳錯誤不能被回報為完全沒有刪除。 */
    @Test
    fun `failure after SQL commit preserves actual removal count`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = HistoryRetentionCoordinator()
        val writer = writer(dispatcher, coordinator)
        val path = createTempDirectory("history-management-partial-").resolve("history.sqlite")
        try {
            writer.attach(path)
            runCurrent()
            val database = SqliteHistoryDatabase.open(path)
            val id = Uuid.random().toString()
            database.recordTerminals(listOf(HistoryTerminalRecord(id, Uuid.random().toString(), 100L, false)))
            DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
                connection.createStatement().use { it.executeUpdate("INSERT INTO history_tombstone VALUES ('invalid-id', 1, 'test')") }
            }
            coordinator.apply(MinecraftHistoryConfig(includeInterruptedMatches = false, retentionDays = 0).retentionPolicy())
            val result = assertIs<HistoryManagementResult.Failed>(writer.runCleanup())
            val report = assertNotNull(result.cleanup)
            assertEquals(1L, report.removedMatches)
            assertFalse(report.completed)
            assertFalse(report.storageAvailable)
            assertNull(report.recoveryBusy)
            assertNull(report.incrementalSupported)
            assertTrue(id in database.readTombstones())
            assertNotNull(result.cachedSnapshot)
        } finally {
            writer.detach()
        }
    }

    /** 同時工作有界拒絕；取消後釋放鎖，世界切換後舊要求失效。 */
    @Test
    fun `busy cancellation and session replacement do not reuse old results`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = HistoryRetentionCoordinator()
        val writer = writer(dispatcher, coordinator)
        try {
            writer.attach(createTempDirectory("history-management-session-").resolve("history.sqlite"))
            runCurrent()
            val original = assertNotNull(writer.currentSessionId)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val lease = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.withPolicy {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            val pending = async { writer.storage(original) }
            runCurrent()
            assertIs<HistoryManagementResult.Busy>(writer.previewCleanup(original))
            pending.cancelAndJoin()
            release.complete(Unit)
            lease.join()
            assertIs<HistoryManagementResult.Success<HistoryStorageSnapshot>>(writer.storage(original))
            writer.detach()
            assertIs<HistoryManagementResult.SessionChanged>(writer.storage(original))
            assertIs<HistoryManagementResult.Disconnected>(writer.storage())
            writer.attach(createTempDirectory("history-management-new-session-").resolve("history.sqlite"))
            assertNotEquals(original, writer.currentSessionId)
            assertIs<HistoryManagementResult.SessionChanged>(writer.runCleanup(original))
            runCurrent()
            val enteredAgain = CompletableDeferred<Unit>()
            val releaseAgain = CompletableDeferred<Unit>()
            val leaseAgain = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.withPolicy {
                    enteredAgain.complete(Unit)
                    releaseAgain.await()
                }
            }
            enteredAgain.await()
            val oldWork = async { writer.storage() }
            runCurrent()
            val detach = async { writer.detach() }
            runCurrent()
            assertTrue(oldWork.isCancelled, "Detach must cancel pending management work")
            releaseAgain.complete(Unit)
            leaseAgain.join()
            detach.await()
        } finally {
            writer.detach()
        }
    }

    /**
     * 建立使用可控制 dispatcher 的正式 writer，不替換資料庫或維護服務。
     *
     * @param dispatcher 測試排程器，避免背景輪詢與管理要求競速。
     * @param coordinator 共用政策 lease。
     * @return 尚未綁定 session 的 writer。
     */
    private fun writer(dispatcher: CoroutineDispatcher, coordinator: HistoryRetentionCoordinator): FabricHistoryOutboxWriter = FabricHistoryOutboxWriter(
        store = AuthoritativeStateStore(),
        registries = buildBuiltInPersistenceRegistries(),
        json = Json,
        moduleRegistry = MahjongModuleRegistryImpl(),
        locations = TableLocationRegistry(),
        configState = MinecraftServerConfigState(MinecraftServerConfig(history = MinecraftHistoryConfig(includeInterruptedMatches = true, retentionDays = 0))),
        retentionCoordinator = coordinator,
        dispatchers = object : CoroutineDispatchers {
            override val default = dispatcher
            override val io = dispatcher
            override val main = dispatcher
        },
    )
}
