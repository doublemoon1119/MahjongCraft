package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryCapturePersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 在獨立 I/O 協程中將權威 outbox 送入 SQLite；只在交易成功後確認已寫入的事件。
 *
 * @property store 權威待寫事件與精確確認的來源。
 * @param registries 歷史事件的擴充型別轉換表；只用於建立 mapper，不保留為成員。
 * @property json 事件 payload 的序列化設定。
 * @property dispatchers 平台提供的 I/O dispatcher。
 */
@Single
class FabricHistoryOutboxWriter(
    private val store: AuthoritativeStateStore,
    registries: PersistenceRegistries,
    private val json: Json,
    private val dispatchers: CoroutineDispatchers,
) {
    private val logger = LoggerFactory.getLogger(FabricHistoryOutboxWriter::class.java)
    private val mapper = HistoryCapturePersistenceMapper(registries, json)
    private var database: SqliteHistoryDatabase? = null
    private var worker: Job? = null

    /** 開啟目前世界的資料庫；失敗時不啟用採集，也不影響對局。 */
    suspend fun attach(server: MinecraftServer) = attach(FabricHistoryDatabasePath.resolve(server))

    /** 路徑版本供無 Minecraft server 的整合測試使用。 */
    internal suspend fun attach(path: Path) {
        check(worker == null) { "History writer is already attached" }
        val opened = try {
            withContext(dispatchers.io) { SqliteHistoryDatabase.open(path) }
        } catch (error: Exception) {
            logger.error("History database could not be opened; capture is disabled for this session", error)
            return
        }
        database = opened
        store.setHistoryCaptureEnabled(true)
        worker = CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            var retryDelay = INITIAL_RETRY_DELAY
            while (true) {
                try {
                    val wrote = flushOneBatch(opened)
                    retryDelay = INITIAL_RETRY_DELAY
                    if (!wrote) delay(IDLE_POLL_INTERVAL)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    logger.error("History outbox write failed; pending events remain in the authoritative save", error)
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
                }
            }
        }
    }

    /** 停止背景工作並有界嘗試提交最後的待寫事件；未提交者仍留在世界存檔。 */
    suspend fun detach() {
        worker?.cancelAndJoin()
        worker = null
        store.setHistoryCaptureEnabled(false)
        val activeDatabase = database ?: return
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
    }

    /** 對一份不可變快照提交最多一批，確認時只移除該批的穩定鍵。 */
    private suspend fun flushOneBatch(activeDatabase: SqliteHistoryDatabase): Boolean {
        val batch = store.snapshot().historyCaptureState.pendingEvents.take(BATCH_SIZE)
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
