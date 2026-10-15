package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessStepTimer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryDatabasePath
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HISTORY_WRITER_IDLE_POLL_INTERVAL
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HISTORY_WRITE_BATCH_SIZE
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource

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
class StressTestEnvironmentFactory(
    private val registries: PersistenceRegistries,
    private val json: Json,
    private val dispatchers: CoroutineDispatchers,
    private val moduleRegistry: MahjongModuleRegistry,
    private val locations: TableLocationRegistry,
    private val replayProjectionRegistry: HistoryReplayProjectionRegistry,
) {
    private val logger = mahjongCraftLogger(StressTestEnvironmentFactory::class)

    /**
     * 建立環境；[StressHistoryMode.WRITE] 時在 [server] 的存檔資料夾建立新的壓力測試資料庫，同名舊檔會先刪除。
     *
     * @return 環境；資料庫無法開啟時為 null。
     *
     * @param server 用來解析存檔資料夾的伺服器。
     * @param historyMode 歷史處理方式。
     */
    suspend fun open(server: MinecraftServer, historyMode: StressHistoryMode): StressTestEnvironment? = openAt(FabricHistoryDatabasePath.resolve(server).resolveSibling(DATABASE_FILE_NAME), historyMode)

    /**
     * 建立環境；[StressHistoryMode.WRITE] 時在 [path] 建立新的壓力測試資料庫，同名舊檔會先刪除。
     *
     * @return 環境；資料庫無法開啟時為 null。
     *
     * @param path 壓力測試資料庫所在路徑。
     * @param historyMode 歷史處理方式。
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

    /**
     * 以正式寫入元件開啟 [path] 的新資料庫；無法開啟時刪除殘檔並回傳 null。
     *
     * @param store 接收歷史事件的權威狀態來源。
     * @param writerTimer 記錄背景寫入各環節的計時器。
     * @param path 新資料庫的檔案路徑。
     * @return 可用的資料庫歷史接收器；開啟失敗時為 null。
     */
    private suspend fun openDatabase(
        store: AuthoritativeStateStore,
        writerTimer: StressWriterTimer,
        path: Path,
    ): StressHistorySink.Database? {
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

    /**
     * 啟動只編碼不寫入的背景工作。
     *
     * @param store 提供待編碼歷史事件的權威狀態來源。
     * @param writerTimer 記錄背景編碼耗時與事件數的計時器。
     * @return 執行中的編碼接收器。
     */
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
