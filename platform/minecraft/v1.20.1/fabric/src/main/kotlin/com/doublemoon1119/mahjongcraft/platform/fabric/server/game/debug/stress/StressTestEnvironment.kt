package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessStepTimer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryDatabasePath
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HISTORY_WRITER_IDLE_POLL_INTERVAL
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HISTORY_WRITE_BATCH_SIZE
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryDataSource
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryRetentionCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.encodePendingRecord
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.retentionPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * 壓力測試專用的資料環境：所有測試對局共用的權威來源、分項計時器，以及依 [StressHistoryMode] 處理歷史的背景工作。
 *
 * 歷史不接觸正式對局的待寫佇列，也不套用正式的保留與記錄政策。
 *
 * @property store 所有測試對局共用的權威來源。
 * @property historyMode 歷史處理方式。
 * @property historyTimer [store] 的歷史記錄觀察者，累計歷史記錄耗時與新加入待寫佇列的事件數；各桌的其他環節由各桌自己的計時器累計。
 * @property writerTimer 累計歷史背景工作各環節耗時與已處理事件數。
 * @property sink 處理待寫佇列的背景工作。
 */
class StressTestEnvironment internal constructor(
    val store: AuthoritativeStateStore,
    val historyMode: StressHistoryMode,
    val historyTimer: HeadlessStepTimer,
    val writerTimer: StressWriterTimer,
    private val sink: StressHistorySink,
) {
    /** 對局歷史畫面讀取壓力測試紀錄的資料來源；只有寫進資料庫的模式才有。 */
    val historySource: HistoryDataSource? = (sink as? StressHistorySink.Database)?.let { HistoryDataSource(it.writer, store) }

    /** 處理歷史的背景工作是否發生錯誤或暫停。 */
    suspend fun historyFailed(): Boolean = sink.failed()

    /** 停止背景工作；有資料庫時一併刪除資料庫及其附屬檔案。 */
    suspend fun closeAndDelete() = sink.close()
}

/**
 * 以同一筆交易把多桌從共用權威來源移除：打完後留下的房間，或尚未結束的對局連同房間一併移除，讓持續補桌或停止測試後
 * 狀態不會殘留。
 *
 * 已加入待寫佇列的歷史仍留給背景工作處理；未結束的對局依一般流程記為未完成的場次。
 *
 * @param store 這些桌所在的共用權威來源。
 * @param venueIds 要移除的桌的場地識別碼。
 */
internal suspend fun discardStressTables(store: AuthoritativeStateStore, venueIds: Collection<Uuid>) {
    if (venueIds.isEmpty()) return
    store.update { state ->
        AuthoritativeStateUpdate(state.copy(rooms = state.rooms - venueIds.toSet(), games = state.games - venueIds.toSet()), Unit)
    }
}

/** 處理壓力測試待寫佇列的背景工作。 */
sealed interface StressHistorySink {
    /** 是否發生錯誤或暫停。 */
    suspend fun failed(): Boolean

    /** 停止背景工作並刪除產生的檔案。 */
    suspend fun close()

    /**
     * 以正式寫入流程寫進獨立資料庫。
     *
     * @property writer 背景寫入元件。
     * @property databasePath 壓力測試資料庫檔案。
     */
    class Database(val writer: FabricHistoryOutboxWriter, val databasePath: Path) : StressHistorySink {
        override suspend fun failed(): Boolean = writer.status().let { !it.databaseConnected || it.storagePaused || it.lastError != null }

        override suspend fun close() {
            writer.detach()
            DATABASE_FILE_SUFFIXES.forEach { suffix -> Files.deleteIfExists(databasePath.resolveSibling(databasePath.fileName.toString() + suffix)) }
        }

        private companion object {
            /** 資料庫主檔與 SQLite WAL／SHM 附屬檔的檔名後綴。 */
            val DATABASE_FILE_SUFFIXES: List<String> = listOf("", "-wal", "-shm")
        }
    }

    /**
     * 以與寫入元件相同的批次大小與輪詢間隔，把待寫事件編碼成持久化格式後直接確認移除；場次結束後的記錄狀態也一併確認，
     * 如同已封存。
     *
     * @property job 背景工作。
     * @property failure 背景工作是否已因錯誤停止。
     */
    class EncodeOnly internal constructor(private val job: Job, private val failure: () -> Boolean) : StressHistorySink {
        override suspend fun failed(): Boolean = failure()

        override suspend fun close() = job.cancelAndJoin()
    }

    /** 不記錄歷史，沒有背景工作。 */
    data object Disabled : StressHistorySink {
        override suspend fun failed(): Boolean = false

        override suspend fun close() = Unit
    }
}

/**
 * 建立 [StressTestEnvironment]。
 *
 * @property registries 歷史持久化映射使用的 registry。
 * @property json 歷史持久化的序列化設定。
 * @property dispatchers 背景工作使用的 dispatcher。
 * @property moduleRegistry 封存 Replay 時解析規則用。
 * @property locations 封存 Replay 時解析場地用。
 * @property replayProjectionRegistry 封存 Replay 的投影 registry。
 */
@Single
class StressTestEnvironmentFactory(
    @Provided private val registries: PersistenceRegistries,
    private val json: Json,
    private val dispatchers: CoroutineDispatchers,
    @Provided private val moduleRegistry: MahjongModuleRegistry,
    private val locations: TableLocationRegistry,
    @Provided private val replayProjectionRegistry: HistoryReplayProjectionRegistry,
) {
    /** 記錄編碼背景工作錯誤的 logger。 */
    private val logger = mahjongCraftLogger(StressTestEnvironmentFactory::class)

    /**
     * 建立環境；[StressHistoryMode.WRITE] 時在 [server] 的存檔資料夾建立新的壓力測試資料庫，同名舊檔會先刪除。
     *
     * @return 環境；資料庫無法開啟時為 null。
     */
    suspend fun open(server: MinecraftServer, historyMode: StressHistoryMode): StressTestEnvironment? = openAt(FabricHistoryDatabasePath.resolve(server).resolveSibling(DATABASE_FILE_NAME), historyMode)

    /**
     * 建立環境；[StressHistoryMode.WRITE] 時在 [path] 建立新的壓力測試資料庫，同名舊檔會先刪除。
     *
     * @return 環境；資料庫無法開啟時為 null。
     */
    internal suspend fun openAt(path: Path, historyMode: StressHistoryMode): StressTestEnvironment? {
        val historyTimer = HeadlessStepTimer()
        val writerTimer = StressWriterTimer()
        val store = AuthoritativeStateStore(historyRecordingEnabled = historyMode != StressHistoryMode.OFF, historyRecordingObserver = historyTimer)
        val sink = when (historyMode) {
            StressHistoryMode.WRITE -> openDatabase(store, writerTimer, path) ?: return null
            StressHistoryMode.ENCODE -> startEncoding(store, writerTimer)
            StressHistoryMode.OFF -> StressHistorySink.Disabled
        }
        return StressTestEnvironment(store, historyMode, historyTimer, writerTimer, sink)
    }

    /** 以正式寫入元件開啟 [path] 的新資料庫；無法開啟時刪除殘檔並回傳 null。 */
    private suspend fun openDatabase(store: AuthoritativeStateStore, writerTimer: StressWriterTimer, path: Path): StressHistorySink.Database? {
        val retention = HistoryRetentionCoordinator().apply { apply(STRESS_HISTORY_CONFIG.retentionPolicy()) }
        val writer = FabricHistoryOutboxWriter(
            store = store,
            registries = registries,
            json = json,
            dispatchers = dispatchers,
            moduleRegistry = moduleRegistry,
            locations = locations,
            configState = MinecraftServerConfigState(MinecraftServerConfig(history = STRESS_HISTORY_CONFIG)),
            retentionCoordinator = retention,
            replayProjectionRegistry = replayProjectionRegistry,
            observer = writerTimer,
        )
        val sink = StressHistorySink.Database(writer, path)
        sink.close()
        writer.attach(path)
        return if (writer.status().databaseConnected) sink else null.also { sink.close() }
    }

    /** 啟動只編碼不寫入的背景工作。 */
    private fun startEncoding(store: AuthoritativeStateStore, writerTimer: StressWriterTimer): StressHistorySink.EncodeOnly {
        val mapper = HistoryRecordingPersistenceMapper(registries, json)
        val failed = AtomicBoolean(false)
        val job = CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                while (true) {
                    val batch = store.snapshot().historyRecordingState.pendingEvents.take(HISTORY_WRITE_BATCH_SIZE)
                    if (batch.isEmpty()) {
                        delay(HISTORY_WRITER_IDLE_POLL_INTERVAL)
                        continue
                    }
                    val mark = TimeSource.Monotonic.markNow()
                    batch.forEach { event -> mapper.encodePendingRecord(event, json) }
                    writerTimer.onStage(HistoryWriterStage.ENCODE, mark.elapsedNow())
                    store.acknowledgeHistoryEvents(batch.mapTo(mutableSetOf()) { it.matchId to it.sequence })
                    writerTimer.onEventsWritten(batch.size)
                    val ended = store.snapshot().historyRecordingState.terminalByMatchId.keys
                    if (ended.isNotEmpty()) store.acknowledgeHistoryMetadata(ended)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed.set(true)
                logger.error("Stress test history encoding failed", error)
            }
        }
        return StressHistorySink.EncodeOnly(job, failed::get)
    }

    private companion object {
        /** 壓力測試資料庫的檔名，與正式歷史資料庫放在同一個資料夾。 */
        const val DATABASE_FILE_NAME = "stress-test-history.sqlite"

        /** 記錄全 AI 對局、不清理任何紀錄、磁碟不設實際上限的歷史設定。 */
        val STRESS_HISTORY_CONFIG = MinecraftHistoryConfig(
            enabled = true,
            includeAiMatches = true,
            maxMatches = 0,
            retentionDays = 0,
            maxDiskMiB = MinecraftHistoryConfig.MAX_MAX_DISK_MIB,
        )
    }
}
