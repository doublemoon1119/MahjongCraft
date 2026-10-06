package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證已完成的百場資料庫可在不重跑對局的情況下接受保留與容量政策。 */
class HistoryGenerationRetentionStressTest {
    /** 驗證十場上限會保留最新資料，並為至少九十場留下清理 tombstone。 */
    @Test
    fun `retention policy prunes copied hundred match database`() = runBlocking {
        val source = stressDatabase() ?: return@runBlocking
        val copy = copyDatabase(source)
        val writer = writer(AuthoritativeStateStore(historyRecordingEnabled = true), maxMatches = 10)
        try {
            writer.attach(copy)
            val session = checkNotNull(writer.currentSessionId)
            writer.runCleanup(session)
            val database = SqliteHistoryDatabase.open(copy)
            assertEquals(10, database.readReplayIds().size, "The max-matches policy must retain exactly ten replays.")
            assertEquals(90, database.readTombstones().size, "Exactly ninety removed matches must have tombstones.")
        } finally {
            writer.detach()
        }
    }

    /** 驗證一 MiB 上限會回收資料或暫停儲存並拒絕新的歷史轉移。 */
    @Test
    fun `disk policy prunes or pauses copied hundred match database`() = runBlocking {
        val source = stressDatabase() ?: return@runBlocking
        val copy = copyDatabase(source)
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val writer = writer(store, maxDiskMiB = 1)
        try {
            writer.attach(copy)
            val session = checkNotNull(writer.currentSessionId)
            val database = SqliteHistoryDatabase.open(copy)
            val usage = database.measureDiskUsage()
            val status = writer.status()
            if (usage.totalBytes > 1L * 1024L * 1024L) {
                assertTrue(status.storagePaused, "Storage must pause when the disk policy cannot be satisfied.")
                val result = writer.beginGeneration(Game(tableState = FakeTableStateFactory.create(), flowConfig = GameFlowConfig()), session)
                assertTrue(
                    result is HistoryManagementResult.Success && !result.value,
                    "A paused history store must reject a new generation.",
                )
            }
        } finally {
            writer.detach()
        }
    }

    /** 取得並驗證百場壓力測試資料庫；未提供路徑時略過需要既有資料的測試。
     *
     * @return 僅供複製的既有資料庫路徑，或環境變數未設定時的 null。
     */
    private fun stressDatabase(): Path? = System.getenv("MAHJONGCRAFT_HISTORY_STRESS_DATABASE")
        ?.takeIf { it.isNotBlank() }
        ?.let(Path::of)

    /** 將既有資料庫及 SQLite sidecar 複製到獨立暫存目錄，避免改動來源檔案。
     *
     * @param source 已完成百場資料庫的主檔路徑。
     * @return 複製後獨立資料庫的主檔路徑。
     */
    private fun copyDatabase(source: Path): Path {
        val directory = createTempDirectory("mahjongcraft-history-retention-stress-")
        listOf(source, Path.of("$source-wal"), Path.of("$source-shm")).filter(Files::exists).forEach { file ->
            Files.copy(file, directory.resolve(file.fileName.toString()))
        }
        return directory.resolve(source.fileName.toString()).also { copy ->
            assertEquals(100, SqliteHistoryDatabase.open(copy).readReplayIds().size, "The supplied stress database must contain exactly 100 archived matches.")
        }
    }

    /** 建立使用正式 persistence registry 與 SQLite 邊界的 writer。
     *
     * @param store writer 使用的權威待寫狀態。
     * @param maxMatches 覆蓋測試用的場數上限；null 使用預設值。
     * @param maxDiskMiB 覆蓋測試用的磁碟上限；null 使用預設值。
     * @return 尚未附加資料庫的歷史 writer。
     */
    private fun writer(
        store: AuthoritativeStateStore,
        maxMatches: Int? = null,
        maxDiskMiB: Long? = null,
    ): FabricHistoryOutboxWriter {
        val history = MinecraftHistoryConfig(
            includeAiMatches = true,
            maxMatches = maxMatches ?: MinecraftHistoryConfig.DEFAULT_MAX_MATCHES,
            maxDiskMiB = maxDiskMiB ?: MinecraftHistoryConfig.DEFAULT_MAX_DISK_MIB,
        )
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        return FabricHistoryOutboxWriter(
            store = store,
            registries = bundledPersistenceRegistries(),
            replayProjectionRegistry = buildTestHistoryReplayProjectionRegistry(),
            json = Json,
            dispatchers = TestDispatchers,
            moduleRegistry = modules,
            locations = TableLocationRegistry(),
            configState = MinecraftServerConfigState(MinecraftServerConfig(history = history)),
            retentionCoordinator = HistoryRetentionCoordinator(),
        )
    }

    /** 測試使用的非阻塞 dispatcher 邊界。 */
    private object TestDispatchers : CoroutineDispatchers {
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
        override val main = Dispatchers.Default
    }
}
