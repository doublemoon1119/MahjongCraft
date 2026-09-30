package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.sql.SQLException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * 管理員可見的歷史寫入狀態，不包含資料庫路徑或事件內容。
 *
 * @property databaseConnected 是否已開啟目前世界的資料庫。
 * @property recordingEnabled 有效政策是否允許新對局記錄歷史；不表示每場皆符合資格或資料庫已連線。
 * @property pendingEventCount 權威 outbox 尚待寫入的事件數。
 * @property knownGapCount 已知具有序號缺口的場次數。
 * @property lastError 最近是否發生錯誤；原始文字只供內部診斷，不傳給玩家。
 * @property storagePaused 是否因實際容量不足或量測失敗暫停新增歷史，與有效總開關分離。
 */
data class HistoryWriterStatus(
    val databaseConnected: Boolean,
    val recordingEnabled: Boolean,
    val pendingEventCount: Int,
    val knownGapCount: Int,
    val lastError: String?,
    val storagePaused: Boolean = false,
)

/** 正式管理員重試連線的結果，不公開原始例外細節。 */
sealed interface HistoryRetryResult {
    /** 已重新連結並開始處理待寫事件。 */
    data object Reconnected : HistoryRetryResult

    /** 原本已連線，不建立第二個 writer。 */
    data object AlreadyConnected : HistoryRetryResult

    /** 開啟或驗證失敗，保留原資料。 */
    data object Failed : HistoryRetryResult
}

/**
 * 在獨立 I/O 協程中將權威 outbox 送入 SQLite；只在交易成功後確認已寫入的事件。
 *
 * @property store 權威待寫事件與精確確認的來源。
 * @param registries 歷史事件的擴充型別轉換表；只用於建立 mapper，不保留為成員。
 * @property json 事件 payload 的序列化設定。
 * @property dispatchers 平台提供的 I/O dispatcher。
 * @param moduleRegistry 解析開局使用的規則模組；只交給封存服務。
 * @param locations 取得牌桌位置摘要；只交給封存服務。
 * @property configState 目前有效的不可變設定，供 session 初始化使用。
 * @property retentionCoordinator 將清理與有效政策更新排序的協調邊界。
 */
@Single
class FabricHistoryOutboxWriter(
    private val store: AuthoritativeStateStore,
    registries: PersistenceRegistries,
    private val json: Json,
    private val dispatchers: CoroutineDispatchers,
    moduleRegistry: MahjongModuleRegistry,
    locations: TableLocationRegistry,
    @Provided private val configState: MinecraftServerConfigState,
    private val retentionCoordinator: HistoryRetentionCoordinator,
) {
    /** 記錄歷史寫入與對帳錯誤的 logger。 */
    private val logger = LoggerFactory.getLogger(FabricHistoryOutboxWriter::class.java)

    /** 將權威事件映射成歷史持久化 DTO。 */
    private val mapper = HistoryRecordingPersistenceMapper(registries, json)

    /** 對帳待寫事件並建立完整 Replay 的服務。 */
    private val archiveService = HistoryArchiveService(mapper, registries, moduleRegistry, locations, json)

    /** 共用唯讀 preview 與清理政策的 I/O 維護服務。 */
    private val retentionService = HistoryRetentionService(store)

    /** 保護資料庫連線與背景 worker 的 session 鎖。 */
    private val sessionMutex = Mutex()

    /** 最近一次非預期寫入錯誤的安全摘要。 */
    @Volatile private var lastError: String? = null

    /** 最近一次對帳已知的序號缺口。 */
    @Volatile private var knownGaps: Map<String, Long> = emptyMap()

    /** 目前是否已建立可用的資料庫 session。 */
    @Volatile private var connected = false

    /** 此連線已成功同步的設定停止診斷，避免輪詢時重複寫入相同資料。 */
    private var synchronizedStops: Map<String, String> = emptyMap()

    /** 目前 session 的資料庫邊界。 */
    private var database: SqliteHistoryDatabase? = null

    /** 目前 session 的背景寫入 worker。 */
    private var worker: Job? = null

    /** 開啟目前世界的資料庫；連線狀態不改變資格政策，失敗時待寫事件仍由有界佇列保留。
     *
     * @param server 目前執行中的 Minecraft 伺服器。
     */
    suspend fun attach(server: MinecraftServer) = attach(FabricHistoryDatabasePath.resolve(server))

    /** 回報連線與權威待寫狀態；原始錯誤只供內部 log 使用。
     *
     * @return 不包含路徑與 payload 的安全狀態摘要。
     */
    suspend fun status(): HistoryWriterStatus {
        val recording = store.snapshot().historyRecordingState
        return HistoryWriterStatus(
            databaseConnected = connected,
            recordingEnabled = store.isHistoryRecordingEnabled,
            pendingEventCount = recording.pendingEvents.size,
            knownGapCount = (knownGaps.keys + recording.firstMissingSequenceByMatchId.keys.map { it.toString() }).size,
            lastError = lastError ?: archiveService.lastArchiveError,
            storagePaused = !store.isHistoryStorageAvailable,
        )
    }

    /** 僅在斷線時重新開啟目前存檔的固定資料庫，不建立第二個 worker。
     *
     * @param server 目前執行中的 Minecraft 伺服器。
     * @return 重新連線、已連線或失敗結果。
     */
    suspend fun retry(server: MinecraftServer): HistoryRetryResult = sessionMutex.withLock {
        if (connected) return@withLock HistoryRetryResult.AlreadyConnected
        worker?.cancelAndJoin()
        worker = null
        if (open(FabricHistoryDatabasePath.resolve(server))) HistoryRetryResult.Reconnected else HistoryRetryResult.Failed
    }

    /** 路徑版本供無 Minecraft server 的整合測試使用。
     *
     * @param path 歷史資料庫檔案位置。
     */
    internal suspend fun attach(path: Path) = sessionMutex.withLock {
        check(worker == null && database == null) { "History writer is already attached" }
        lastError = null
        knownGaps = emptyMap()
        synchronizedStops = emptyMap()
        archiveService.resetSession()
        store.applyHistoryStorageAvailability(true)
        retentionCoordinator.apply(configState.current.history.retentionPolicy())
        open(path)
    }

    /** 開啟、驗證並對帳；失敗時保留原資料與有效記錄政策。
     *
     * @param path 歷史資料庫檔案位置。
     * @return 是否成功建立背景寫入 session。
     */
    private suspend fun open(path: Path): Boolean {
        val opened = try {
            withContext(dispatchers.io) { SqliteHistoryDatabase.open(path) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            lastError = error.message ?: error::class.simpleName
            logger.error("History database could not be opened; pending events remain in the bounded outbox", error)
            return false
        }
        database = opened
        try {
            withContext(dispatchers.io) {
                retentionCoordinator.withPolicy { policy ->
                    synchronizedStops = opened.readRecordingStops()
                    synchronizeRecordingDecisions(opened)
                    val snapshot = store.snapshot()
                    archiveService.reconcile(opened, snapshot.historyRecordingState).also {
                        knownGaps = it
                        archiveService.archiveReady(opened, snapshot)
                    }
                    maintain(opened, policy)
                }
            }
        } catch (cancelled: CancellationException) {
            database = null
            connected = false
            throw cancelled
        } catch (error: Exception) {
            database = null
            connected = false
            lastError = error.message ?: error::class.simpleName
            logger.error("History startup reconciliation failed; pending events remain in the bounded outbox", error)
            return false
        }
        connected = true
        lastError = null
        worker = CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            var retryDelay = INITIAL_RETRY_DELAY
            var maintenanceMark = TimeSource.Monotonic.markNow()
            var lastPolicy: HistoryRetentionPolicy? = null
            while (true) {
                try {
                    val wrote = retentionCoordinator.withPolicy { policy ->
                        val pressure = opened.measureDiskUsage().totalBytes > policy.maxDiskBytes
                        if (lastPolicy != policy || maintenanceMark.elapsedNow() >= MAINTENANCE_INTERVAL || (pressure && store.isHistoryStorageAvailable)) {
                            synchronizeRecordingDecisions(opened)
                            val snapshot = store.snapshot()
                            knownGaps = archiveService.reconcile(opened, snapshot.historyRecordingState)
                            archiveService.archiveReady(opened, snapshot)
                            maintain(opened, policy)
                            lastPolicy = policy
                            maintenanceMark = TimeSource.Monotonic.markNow()
                        }
                        if (!store.isHistoryStorageAvailable) return@withPolicy false
                        val wrote = flushOneBatch(opened)
                        if (wrote) {
                            val snapshot = store.snapshot()
                            if (snapshot.historyRecordingState.pendingEvents.isEmpty()) {
                                knownGaps = archiveService.reconcile(opened, snapshot.historyRecordingState)
                                if (archiveService.archiveReady(opened, snapshot) > 0) maintain(opened, policy)
                            }
                        }
                        wrote
                    }
                    retryDelay = INITIAL_RETRY_DELAY
                    if (!wrote) delay(IDLE_POLL_INTERVAL)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    lastError = error.message ?: error::class.simpleName
                    logger.error("History outbox write failed; pending events remain in the authoritative save", error)
                    if (error is SQLException) {
                        connected = false
                        database = null
                        return@launch
                    }
                    store.applyHistoryStorageAvailability(false)
                    lastPolicy = null
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
                }
            }
        }
        return true
    }

    /** 停止背景工作並有界嘗試提交最後的待寫事件；未提交者仍留在世界存檔。 */
    suspend fun detach() = sessionMutex.withLock {
        worker?.cancelAndJoin()
        worker = null
        val activeDatabase = database ?: return@withLock
        withTimeoutOrNull(SHUTDOWN_FLUSH_TIMEOUT) {
            withContext(dispatchers.io) {
                try {
                    retentionCoordinator.withPolicy { policy ->
                        while (store.isHistoryStorageAvailable && activeDatabase.measureDiskUsage().totalBytes <= policy.maxDiskBytes && flushOneBatch(activeDatabase)) {
                            /* 已提交事件逐批確認，容量不足時保持待寫狀態。 */
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    logger.error("History outbox shutdown flush failed; pending events remain in the authoritative save", error)
                }
            }
        } ?: logger.warn("History outbox shutdown flush timed out; pending events remain in the authoritative save")
        database = null
        connected = false
    }

    /**
     * 套用清理政策後重新讀取診斷，避免已刪除場次仍留在狀態統計中。
     *
     * @param activeDatabase 同一 session 的資料庫。
     * @param policy 協調邊界固定的有效政策。
     */
    private suspend fun maintain(activeDatabase: SqliteHistoryDatabase, policy: HistoryRetentionPolicy) {
        try {
            retentionService.run(activeDatabase, policy)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            store.applyHistoryStorageAvailability(false)
            throw error
        }
        knownGaps = activeDatabase.readGaps()
        synchronizedStops = activeDatabase.readRecordingStops()
    }

    /** 對一份不可變快照提交最多一批，確認時只移除該批的穩定鍵。
     *
     * @param activeDatabase 目前 session 的固定資料庫。
     * @return 是否成功提交至少一筆事件。
     */
    private suspend fun flushOneBatch(activeDatabase: SqliteHistoryDatabase): Boolean {
        synchronizeRecordingDecisions(activeDatabase)
        val pruned = activeDatabase.readTombstones()
        val batch = store.snapshot().historyRecordingState.pendingEvents
            .filterNot { it.matchId.toString() in archiveService.blockedMatchIds || it.matchId.toString() in pruned }
            .take(BATCH_SIZE)
        if (batch.isEmpty()) return false
        val records = batch.map { event ->
            val dto = mapper.encodePendingEvent(event)
            PendingHistoryRecord(
                matchId = dto.matchId,
                sequence = dto.sequence,
                roundNumber = dto.roundNumber,
                occurredAtEpochMillis = dto.occurredAtEpochMillis,
                payloadVersion = PAYLOAD_VERSION,
                payload = json.encodeToString(HistoryOutboxEventPersistenceDto.serializer(), dto),
            )
        }
        activeDatabase.appendPendingBatch(records)
        store.acknowledgeHistoryEvents(batch.map { it.matchId to it.sequence }.toSet())
        return true
    }

    /**
     * 將設定停止原因冪等同步至資料庫，再確認已離開權威狀態的診斷。
     *
     * @param activeDatabase 目前 session 的固定資料庫。
     */
    private suspend fun synchronizeRecordingDecisions(activeDatabase: SqliteHistoryDatabase) {
        store.acknowledgePrunedHistory(activeDatabase.readPruningConfirmations())
        val recording = store.snapshot().historyRecordingState
        activeDatabase.recordTerminals(
            recording.terminalByMatchId.map { (id, terminal) ->
                HistoryTerminalRecord(id.toString(), terminal.tableId.toString(), terminal.endedAtEpochMillis, terminal.completed)
            },
        )
        val stopped = recording.decisionsByMatchId.filterValues {
            it == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED || it == HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE
        }
        val stops = stopped.mapKeys { it.key.toString() }.mapValues { (_, decision) ->
            if (decision == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED) PARTIAL_CONFIG_DISABLED else PARTIAL_STORAGE_UNAVAILABLE
        }
        val unsynchronized = stops.filter { (matchId, reason) -> synchronizedStops[matchId] != reason }
        if (unsynchronized.isNotEmpty()) {
            activeDatabase.recordRecordingStops(unsynchronized)
            synchronizedStops = synchronizedStops + unsynchronized
        }
        val gaps = recording.firstMissingSequenceByMatchId.mapKeys { it.key.toString() }
            .filter { (matchId, sequence) -> sequence < (knownGaps[matchId] ?: Long.MAX_VALUE) }
        if (gaps.isNotEmpty()) {
            activeDatabase.recordGaps(gaps)
            knownGaps = knownGaps + gaps
        }
        val archived = activeDatabase.readReplayIds()
        val completed = (recording.decisionsByMatchId.keys + recording.terminalByMatchId.keys + recording.nextSequenceByMatchId.keys)
            .filterTo(mutableSetOf()) { it.toString() in archived }
        val persistedPartial = activeDatabase.readTerminalPartialIds()
        val partial = recording.terminalByMatchId.keys.filterTo(mutableSetOf()) { it.toString() in persistedPartial }
        store.acknowledgeHistoryDecisions(completed + stopped.keys)
        store.acknowledgeHistoryMetadata(completed + partial)
    }

    private companion object {
        /** 設定停止的穩定資料庫診斷名稱，不代表完整 Replay。 */
        const val PARTIAL_CONFIG_DISABLED: String = "PARTIAL_CONFIG_DISABLED"

        /** 容量不足造成的部分紀錄診斷，不代表管理員停用總開關。 */
        const val PARTIAL_STORAGE_UNAVAILABLE: String = "PARTIAL_STORAGE_UNAVAILABLE"

        /** 每次提交的最大待寫事件數量。 */
        const val BATCH_SIZE = 64

        /** 待寫事件 payload 的持久化版本。 */
        const val PAYLOAD_VERSION = 1

        /** 背景 worker 閒置時的輪詢間隔。 */
        val IDLE_POLL_INTERVAL = 250.milliseconds

        /** 背景 worker 初次重試等待時間。 */
        val INITIAL_RETRY_DELAY = 1.seconds

        /** 背景 worker 重試等待時間上限。 */
        val MAX_RETRY_DELAY = 16.seconds

        /** 正常關機時等待最後批次提交的時間上限。 */
        val SHUTDOWN_FLUSH_TIMEOUT = 5.seconds

        /** 即使沒有新事件，也定期評估到期政策及可回收空間。 */
        val MAINTENANCE_INTERVAL = 1.minutes
    }
}
