package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
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
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.sql.SQLException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 管理員可見的歷史寫入狀態，不包含資料庫路徑或事件內容。
 *
 * @property databaseConnected 是否已開啟目前世界的資料庫。
 * @property recordingEnabled 新權威交易是否正在記錄歷史。
 * @property pendingEventCount 權威 outbox 尚待寫入的事件數。
 * @property knownGapCount 已知具有序號缺口的場次數。
 * @property lastError 最近是否發生錯誤；原始文字只供內部診斷，不傳給玩家。
 */
data class HistoryWriterStatus(
    val databaseConnected: Boolean,
    val recordingEnabled: Boolean,
    val pendingEventCount: Int,
    val knownGapCount: Int,
    val lastError: String?,
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
 */
@Single
class FabricHistoryOutboxWriter(
    private val store: AuthoritativeStateStore,
    registries: PersistenceRegistries,
    private val json: Json,
    private val dispatchers: CoroutineDispatchers,
    moduleRegistry: MahjongModuleRegistry,
    locations: TableLocationRegistry,
) {
    private val logger = LoggerFactory.getLogger(FabricHistoryOutboxWriter::class.java)
    private val mapper = HistoryRecordingPersistenceMapper(registries, json)
    private val archiveService = HistoryArchiveService(mapper, registries, moduleRegistry, locations, json)
    private val sessionMutex = Mutex()

    @Volatile private var lastError: String? = null

    @Volatile private var knownGaps: Map<String, Long> = emptyMap()

    @Volatile private var connected = false
    private var database: SqliteHistoryDatabase? = null
    private var worker: Job? = null

    /** 開啟目前世界的資料庫；失敗時不啟用記錄，也不影響對局。 */
    suspend fun attach(server: MinecraftServer) = attach(FabricHistoryDatabasePath.resolve(server))

    /** 回報連線與權威待寫狀態；原始錯誤只供內部 log 使用。 */
    suspend fun status(): HistoryWriterStatus {
        val recording = store.snapshot().historyRecordingState
        return HistoryWriterStatus(
            databaseConnected = connected,
            recordingEnabled = store.isHistoryRecordingEnabled,
            pendingEventCount = recording.pendingEvents.size,
            knownGapCount = (knownGaps.keys + recording.firstMissingSequenceByMatchId.keys.map { it.toString() }).size,
            lastError = lastError ?: archiveService.lastArchiveError,
        )
    }

    /** 僅在斷線時重新開啟目前存檔的固定資料庫，不建立第二個 worker。 */
    suspend fun retry(server: MinecraftServer): HistoryRetryResult = sessionMutex.withLock {
        if (connected) return@withLock HistoryRetryResult.AlreadyConnected
        worker?.cancelAndJoin()
        worker = null
        if (open(FabricHistoryDatabasePath.resolve(server))) HistoryRetryResult.Reconnected else HistoryRetryResult.Failed
    }

    /** 路徑版本供無 Minecraft server 的整合測試使用。 */
    internal suspend fun attach(path: Path) = sessionMutex.withLock {
        check(worker == null && database == null) { "History writer is already attached" }
        open(path)
    }

    /** 開啟、驗證並對帳；失敗時保留原資料與關閉的記錄狀態。 */
    private suspend fun open(path: Path): Boolean {
        val opened = try {
            withContext(dispatchers.io) { SqliteHistoryDatabase.open(path) }
        } catch (error: Exception) {
            lastError = error.message ?: error::class.simpleName
            logger.error("History database could not be opened; recording is disabled for this session", error)
            return false
        }
        database = opened
        try {
            val snapshot = store.snapshot()
            knownGaps = withContext(dispatchers.io) {
                archiveService.reconcile(opened, snapshot.historyRecordingState).also {
                    archiveService.archiveReady(opened, snapshot)
                }
            }
        } catch (error: Exception) {
            database = null
            connected = false
            lastError = error.message ?: error::class.simpleName
            logger.error("History startup reconciliation failed; recording remains disabled", error)
            return false
        }
        connected = true
        lastError = null
        store.setHistoryRecordingEnabled(true)
        worker = CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            var retryDelay = INITIAL_RETRY_DELAY
            while (true) {
                try {
                    val wrote = flushOneBatch(opened)
                    if (wrote) {
                        val snapshot = store.snapshot()
                        if (snapshot.historyRecordingState.pendingEvents.isEmpty()) {
                            knownGaps = archiveService.reconcile(opened, snapshot.historyRecordingState)
                            archiveService.archiveReady(opened, snapshot)
                        }
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
                        store.setHistoryRecordingEnabled(false)
                        return@launch
                    }
                    val snapshot = store.snapshot()
                    knownGaps = archiveService.reconcile(opened, snapshot.historyRecordingState)
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
        store.setHistoryRecordingEnabled(false)
        val activeDatabase = database ?: return@withLock
        withTimeoutOrNull(SHUTDOWN_FLUSH_TIMEOUT) {
            withContext(dispatchers.io) {
                try {
                    while (flushOneBatch(activeDatabase)) { /* Drain the persisted outbox. */ }
                } catch (error: Exception) {
                    logger.error("History outbox shutdown flush failed; pending events remain in the authoritative save", error)
                }
            }
        } ?: logger.warn("History outbox shutdown flush timed out; pending events remain in the authoritative save")
        database = null
        connected = false
    }

    /** 對一份不可變快照提交最多一批，確認時只移除該批的穩定鍵。 */
    private suspend fun flushOneBatch(activeDatabase: SqliteHistoryDatabase): Boolean {
        val batch = store.snapshot().historyRecordingState.pendingEvents
            .filterNot { it.matchId.toString() in archiveService.blockedMatchIds }
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

    private companion object {
        const val BATCH_SIZE = 64
        const val PAYLOAD_VERSION = 1
        val IDLE_POLL_INTERVAL = 250.milliseconds
        val INITIAL_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 16.seconds
        val SHUTDOWN_FLUSH_TIMEOUT = 5.seconds
    }
}
