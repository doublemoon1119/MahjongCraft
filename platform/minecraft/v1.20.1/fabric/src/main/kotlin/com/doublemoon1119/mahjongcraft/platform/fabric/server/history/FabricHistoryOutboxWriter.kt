package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTransferResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySummaryRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayRoundReader
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayJsonParseLimits
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadResult
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.parseBoundedReplayJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import java.nio.file.Path
import java.sql.SQLException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

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
 * @param registries 歷史事件與 Replay 設定的擴充型別轉換表。
 * @property json 事件 payload 的序列化設定。
 * @property dispatchers 平台提供的 I/O dispatcher。
 * @param moduleRegistry 解析開局使用的規則模組；只交給封存服務。
 * @param locations 取得牌桌位置摘要；只交給封存服務。
 * @property configState 目前有效的不可變設定，供 session 初始化使用。
 * @property retentionCoordinator 將清理與有效政策更新排序的協調邊界。
 * @property replayProjectionRegistry 已完成註冊與凍結的歷史讀取轉換表。
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
    @Provided private val replayProjectionRegistry: HistoryReplayProjectionRegistry,
) {
    /** 記錄歷史寫入與對帳錯誤的 logger。 */
    private val logger = mahjongCraftLogger(FabricHistoryOutboxWriter::class)

    /** 將權威事件映射成歷史持久化 DTO。 */
    private val mapper = HistoryRecordingPersistenceMapper(registries, json)

    /** 封存 Replay 的規則設定 persistence registry 快照。 */
    private val replayRegistries: PersistenceRegistries = registries

    /** 對帳待寫事件並建立完整 Replay 的服務。 */
    private val archiveService = HistoryArchiveService(mapper, registries, moduleRegistry, locations, json)

    /** 共用唯讀 preview 與清理政策的 I/O 維護服務。 */
    private val retentionService = HistoryRetentionService(store)

    /** 與啟動 log 及管理指令共用的統計組合邊界。 */
    private val storageQueryService = HistoryStorageQueryService(store)

    /** 限制同時只存在一項管理工作，不累積無上限等待佇列。 */
    private val managementMutex = Mutex()

    /** 目前管理工作所屬協程，detach 時取消並等待其停止。 */
    @Volatile private var managementJob: Job? = null

    /** 目前存檔 session 識別碼；重連不改變，切換存檔時失效。 */
    @Volatile internal var currentSessionId: Uuid? = null
        private set

    /** 正式 session 所屬 server；無平台實例的測試為 null。 */
    private var attachedServer: MinecraftServer? = null

    /** 此 session 最後一份成功統計；失敗回覆須明示舊快照。 */
    @Volatile private var cachedStorageSnapshot: HistoryStorageSnapshot? = null

    /** 最近一次統計嘗試的政策，失敗也記錄以避免每個 tick 重試。 */
    private var attemptedStoragePolicy: HistoryRetentionPolicy? = null

    /** 最近一次統計嘗試的單調時刻。 */
    private var storageRefreshMark = TimeSource.Monotonic.markNow()

    /** 每 session 最多一次成功的啟動用量摘要。 */
    private var startupStorageLogged = false

    /** 保護資料庫連線與背景 worker 的 session 鎖。 */
    private val sessionMutex = Mutex()

    /** 最近一次非預期寫入錯誤的安全摘要。 */
    @Volatile private var lastError: String? = null

    /** 近期已結束場次的最小查詢證據；避免歷史狀態查詢依賴仍存在的 Game。 */
    private val recentMatchEvidence = LinkedHashMap<String, RecentHistoryMatchEvidence>()

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
    suspend fun attach(server: MinecraftServer) = attachSession(FabricHistoryDatabasePath.resolve(server), server)

    /**
     * 驗證管理要求仍屬於同一存檔；null 只代表目前未綁定 session。
     *
     * @param id 命令建立時捕捉的 session 識別碼。
     * @return 是否仍屬於目前 session。
     */
    internal fun isCurrentSession(id: Uuid?): Boolean = id != null && currentSessionId == id

    /**
     * 記住已結束場次的參與者與固定記錄決策，供保存尚未建立 SQL 參與者列時授權狀態查詢。
     *
     * @param matchId 已結束場次識別碼。
     * @param participantIds 場次參與者 UUID。
     * @param decision 該場權威記錄決策；已無決策證據時為 null。
     */
    internal fun rememberEndedMatch(matchId: Uuid, participantIds: Set<Uuid>, decision: HistoryRecordingDecision?) {
        synchronized(recentMatchEvidence) {
            recentMatchEvidence[matchId.toString()] = RecentHistoryMatchEvidence(participantIds, decision)
            while (recentMatchEvidence.size > RECENT_MATCH_EVIDENCE_LIMIT) recentMatchEvidence.remove(recentMatchEvidence.keys.first())
        }
    }

    /**
     * 在同一 session 與正式資格政策下建立隔離歷史轉移。
     *
     * @param game 已由隔離 Flow 建立的對局，不加入玩家集合。
     * @param sessionId 生成命令建立時的存檔 session。
     * @return 是否通過記錄資格與容量檢查。
     */
    internal suspend fun beginGeneration(game: Game, sessionId: Uuid): HistoryManagementResult<Boolean> = manage(sessionId) { _, _ ->
        val config = configState.current.history
        val eligibility = HistoryRecordingPolicy(config.enabled, config.includeAiMatches)
        eligibility.decide(game) == HistoryRecordingDecision.RECORDING && store.beginHistoryTransfer(game)
    }

    /**
     * 接收一批完整來源交易，沿用唯一正式 outbox 而不直接插入資料庫。
     *
     * @param matchId 來源場次 ID。
     * @param events 有界且保持交易邊界的權威事件。
     * @param sessionId 來源工作固定的存檔 session。
     * @return 接收、背壓或安全停止結果。
     */
    internal suspend fun appendGeneration(matchId: Uuid, events: List<HistoryOutboxEvent>, sessionId: Uuid): HistoryManagementResult<HistoryTransferResult> = manage(sessionId) { activeDatabase, _ ->
        val result = store.appendHistoryTransfer(matchId, events)
        if (result != HistoryTransferResult.STOPPED) flushOneBatch(activeDatabase)
        result
    }

    /**
     * 提交已完成或已中止的來源終點，不改變正式封存驗證。
     *
     * @param matchId 來源場次 ID。
     * @param terminal 來源權威終點。
     * @param sessionId 固定的存檔 session。
     * @return session 管理結果。
     */
    internal suspend fun finishGeneration(matchId: Uuid, terminal: HistoryRecordingTerminal, sessionId: Uuid): HistoryManagementResult<HistoryGenerationReceipt> = manage(sessionId) { activeDatabase, policy ->
        store.finishHistoryTransfer(matchId, terminal)
        synchronizeRecordingDecisions(activeDatabase)
        archiveService.archiveReady(activeDatabase, store.snapshot())
        synchronizeRecordingDecisions(activeDatabase)
        val archived = activeDatabase.readGenerationReceipt(setOf(matchId.toString()))
        maintain(activeDatabase, policy)
        val maintained = activeDatabase.readGenerationReceipt(setOf(matchId.toString()))
        maintained.copy(replayBytes = archived.replayBytes + maintained.replayBytes)
    }

    /**
     * 查詢生成批次已提交的封存／清理證據及磁碟大小。
     *
     * @param matchIds 此批產生的場次 ID，最多一百筆。
     * @param sessionId 固定的存檔 session。
     * @return 有界儲存證據，不包含 Replay 內容。
     */
    internal suspend fun generationReceipt(matchIds: Set<Uuid>, sessionId: Uuid): HistoryManagementResult<HistoryGenerationReceipt> = manage(sessionId) { activeDatabase, _ -> activeDatabase.readGenerationReceipt(matchIds.mapTo(mutableSetOf()) { it.toString() }) }

    /**
     * 在來源失敗時提交中止證據；資料庫失聯亦可保存，不修改其他 session。
     *
     * @param matchId 欲中止的來源場次。
     * @param terminal 來源桌子與中止時間；完整旗標在此強制為 false。
     * @param sessionId 開始生成時固定的 session。
     */
    internal suspend fun abortGeneration(matchId: Uuid, terminal: HistoryRecordingTerminal, sessionId: Uuid) {
        retentionCoordinator.withPolicy {
            if (isCurrentSession(sessionId)) store.finishHistoryTransfer(matchId, terminal.copy(completed = false))
        }
    }

    /**
     * 非同步更新用量；未連線時不重新開庫，已有工作時不加入等待佇列。
     *
     * @param expectedSessionId 原要求所屬 session。
     * @return 新快照或安全的管理狀態。
     */
    internal suspend fun storage(expectedSessionId: Uuid? = currentSessionId): HistoryManagementResult<HistoryStorageSnapshot> = manage(expectedSessionId) { activeDatabase, policy -> refreshStorage(activeDatabase, policy) }

    /**
     * 唯讀預覽目前政策，容量階段只提供追加候選範圍，不保證回收量。
     *
     * @param expectedSessionId 原要求所屬 session。
     * @return 不含逐場 ID 或 payload 的清理預覽。
     */
    internal suspend fun previewCleanup(expectedSessionId: Uuid? = currentSessionId): HistoryManagementResult<HistoryCleanupPreview> = manage(expectedSessionId) { activeDatabase, policy ->
        val plan = retentionService.preview(activeDatabase, policy)
        val disk = activeDatabase.measureDiskUsage()
        val additional = if (disk.totalBytes > policy.maxDiskBytes) plan.remaining else emptyList()
        HistoryCleanupPreview(
            countsByReason = plan.removals.values.groupingBy { it }.eachCount().mapValues { it.value.toLong() },
            logicalBytes = activeDatabase.logicalPayloadBytes(plan.removals.keys),
            disk = disk,
            additionalCandidateCount = additional.size.toLong(),
            additionalLogicalBytes = activeDatabase.logicalPayloadBytes(additional.map { it.matchId }),
            policy = policy,
            evaluatedAt = plan.evaluatedAt,
        )
    }

    /**
     * 手動觸發同一保留政策；部分失敗仍回報已提交的刪除事實。
     *
     * @param expectedSessionId 原要求所屬 session。
     * @return 實際清理結果或附有部分結果的失敗。
     */
    internal suspend fun runCleanup(expectedSessionId: Uuid? = currentSessionId): HistoryManagementResult<HistoryCleanupReport> = manage(expectedSessionId) { activeDatabase, policy ->
        var before: HistoryDiskUsage? = null
        var committed = 0L
        try {
            before = activeDatabase.measureDiskUsage()
            val result = retentionService.run(activeDatabase, policy) { committed = Math.addExact(committed, it.toLong()) }
            knownGaps = activeDatabase.readGaps()
            synchronizedStops = activeDatabase.readRecordingStops()
            refreshStorageSafely(activeDatabase, policy)
            val report = HistoryCleanupReport(
                committed,
                result.diskBefore,
                result.diskAfter,
                result.storageAvailable,
                result.recoveryBusy,
                result.incrementalSupported,
                true,
            )
            logger.info(
                "History cleanup completed: removedMatches={}, diskBeforeBytes={}, diskAfterBytes={}, storageAvailable={}, recoveryBusy={}",
                report.removedMatches,
                report.diskBefore?.totalBytes,
                report.diskAfter?.totalBytes,
                report.storageAvailable,
                report.recoveryBusy,
            )
            report
        } catch (cancelled: CancellationException) {
            logger.info("History cleanup cancelled after {} committed match removal(s)", committed)
            throw cancelled
        } catch (error: Exception) {
            store.applyHistoryStorageAvailability(false)
            val after = runCatching { activeDatabase.measureDiskUsage() }.getOrNull()
            throw HistoryManagementFailure(
                HistoryCleanupReport(committed, before, after, store.isHistoryStorageAvailable, null, null, false),
                error,
            )
        }
    }

    /**
     * 查詢伺服器內部摘要，不授予一般玩家查閱權限。
     *
     * @param limit 每頁 1～100 筆，預設 20。
     * @param cursor 上一頁的穩定游標。
     * @param expectedSessionId 原要求所屬 session。
     * @return 不含活動場次與牌面內容的摘要分頁。
     */
    internal suspend fun summaries(
        limit: Int = 20,
        cursor: HistorySummaryCursor? = null,
        expectedSessionId: Uuid? = currentSessionId,
    ): HistoryManagementResult<HistorySummaryPage> = manage(expectedSessionId) { activeDatabase, _ ->
        val snapshot = store.snapshot()
        val active = snapshot.games.values.mapTo(mutableSetOf()) { it.matchId.toString() } +
            snapshot.historyRecordingState.transfersByMatchId.keys.map { it.toString() }
        activeDatabase.readSummaryPage(limit, cursor, active)
    }

    /**
     * 於唯一資料庫 session 內讀取經授權的玩家歷史清單。
     *
     * @param access 由連線提供的可信身分。
     * @param request 已通過 Flow 驗證的要求。
     * @param sessionId 收到要求時捕捉的世界 session。
     * @param participantIds 名稱篩選解析出的玩家 UUID；null 表示不限制名稱。
     * @return 同一政策 lease 下的有界資料庫結果。
     */
    internal suspend fun queryList(
        access: HistoryQueryAccess,
        request: HistoryListRequest,
        sessionId: Uuid?,
        participantIds: Set<String>? = null,
    ): HistoryManagementResult<SqliteHistoryQueryPage> = manage(sessionId) { activeDatabase, _ ->
        activeDatabase.readHistoryQueryPage(request.toSqliteQuery(access, excludedHistoryMatches(), participantIds))
    }

    /**
     * 於唯一資料庫 session 內讀取經授權的單場摘要。
     *
     * @param access 由連線提供的可信身分。
     * @param request 已通過 Flow 驗證的要求。
     * @param sessionId 收到要求時捕捉的世界 session。
     * @return 不存在、未公開或未授權皆為空頁，不揭露原因差異。
     */
    internal suspend fun querySummary(
        access: HistoryQueryAccess,
        request: HistorySummaryRequest,
        sessionId: Uuid?,
    ): HistoryManagementResult<SqliteHistoryQueryPage> = manage(sessionId) { activeDatabase, _ ->
        activeDatabase.readHistoryQueryPage(
            HistoryListRequest(scope = request.scope, pageSize = 1)
                .toSqliteQuery(access, excludedHistoryMatches()).copy(matchId = request.matchId.toString()),
        )
    }

    /**
     * 在正式資料庫 session 內解碼 Replay header 的開局設定。
     *
     * @param matchId 欲讀取的對局識別碼。
     * @param maximumBytes 允許載入與解析的 Replay UTF-8 位元組上限。
     * @param sessionId 收到要求時捕捉的世界 session。
     * @return 開局時保存的遊戲設定，或受管理邊界限制的失敗結果。
     */
    internal suspend fun readRuleSettings(
        matchId: Uuid,
        maximumBytes: Int,
        sessionId: Uuid?,
    ): HistoryManagementResult<GameConfig> = manage(sessionId) { activeDatabase, _ ->
        when (val read = activeDatabase.readReplayPayload(matchId.toString(), maximumBytes)) {
            HistoryReplayPayloadRead.Missing -> throw IllegalStateException("History replay is not available")
            HistoryReplayPayloadRead.TooLarge -> throw IllegalStateException("History replay exceeds the query size limit")
            is HistoryReplayPayloadRead.Found -> {
                val parsed = parseBoundedReplayJson(read.payload, ReplayJsonParseLimits(maximumUtf8Bytes = maximumBytes), json)
                val document = (parsed as? ReplayReadResult.Success)?.value
                    ?: error("History replay cannot be parsed within query limits")
                CompactReplayCodec.decodeRuleSettings(document, replayRegistries, matchId, json)
            }
        }
    }

    /**
     * 在唯一 session 與政策 lease 內讀取一頁已授權交易。
     * @param access 可信連線身分。
     * @param matchId 對局識別碼。
     * @param queryScope 要求範圍。
     * @param roundNumber 保存局序號。
     * @param startIndex 起始交易索引。
     * @param limit 最多交易筆數。
     * @param sessionId 收到要求時的世界 session。
     * @return 有界事件頁或安全錯誤。
     */
    internal suspend fun queryRoundEvents(
        access: HistoryQueryAccess,
        matchId: Uuid,
        queryScope: HistoryQueryScope,
        roundNumber: Int,
        startIndex: Int,
        limit: Int,
        sessionId: Uuid?,
    ): HistoryManagementResult<HistoryQueryResult<HistoryRoundEvents>> = manage(sessionId) { activeDatabase, _ ->
        readAuthorizedHistoryRound(
            activeDatabase, access, historyQueryPolicy(), matchId, queryScope, excludedHistoryMatches(),
            { parseBoundedReplayJson(it, json = json) },
            { CompactReplayRoundReader(replayProjectionRegistry).readEvents(it, matchId, roundNumber, startIndex, limit) },
            { it.identity },
        )
    }

    /**
     * 在唯一 session 與政策 lease 內重建已授權交易位置。
     * @param access 可信連線身分。
     * @param matchId 對局識別碼。
     * @param queryScope 要求範圍。
     * @param roundNumber 保存局序號。
     * @param position 初始桌況或指定交易後。
     * @param sessionId 收到要求時的世界 session。
     * @return 完整狀態或安全錯誤。
     */
    internal suspend fun queryRoundState(
        access: HistoryQueryAccess,
        matchId: Uuid,
        queryScope: HistoryQueryScope,
        roundNumber: Int,
        position: HistoryRoundPosition,
        sessionId: Uuid?,
    ): HistoryManagementResult<HistoryQueryResult<HistoryRoundState>> = manage(sessionId) { activeDatabase, _ ->
        readAuthorizedHistoryRound(
            activeDatabase, access, historyQueryPolicy(), matchId, queryScope, excludedHistoryMatches(),
            { parseBoundedReplayJson(it, json = json) },
            { CompactReplayRoundReader(replayProjectionRegistry).readState(it, matchId, roundNumber, position) },
            { it.identity },
        )
    }

    /**
     * 在回覆前重新確認場次仍可公開，不再次讀取 Replay。
     * @param access 目前可信身分。
     * @param matchId 原要求對局。
     * @param queryScope 原要求範圍。
     * @param sessionId 原世界 session。
     * @return 確認結果，不攜帶歷史內容。
     */
    internal suspend fun confirmRoundPublication(
        access: HistoryQueryAccess,
        matchId: Uuid,
        queryScope: HistoryQueryScope,
        sessionId: Uuid?,
    ): HistoryManagementResult<HistoryQueryResult<Unit>> = manage(sessionId) { activeDatabase, _ ->
        when (val result = authorizedHistoryReplay(activeDatabase, access, historyQueryPolicy(), matchId, queryScope, excludedHistoryMatches())) {
            is HistoryQueryResult.Failure -> result
            is HistoryQueryResult.Success -> HistoryQueryResult.Success(Unit)
        }
    }

    /** 取得目前有效查詢政策，不沿用要求開始時的設定。
     * @return 記錄政策以外的獨立查詢政策。
     */
    private fun historyQueryPolicy(): HistoryQueryPolicy = configState.current.history.let { HistoryQueryPolicy(it.queryEnabled, it.allowAdminQuery) }

    /**
     * 依權威記錄狀態與 SQLite 證據判定單場歷史保存狀態。
     *
     * @param access 可信查詢身分；管理員只在目前政策允許時可查詢未參與場次。
     * @param matchId 欲查詢的對局識別碼。
     * @param sessionId 收到要求時捕捉的世界 session。
     * @return 不包含 Replay 內容或資料庫內部診斷的穩定狀態。
     */
    internal suspend fun archiveStatus(
        access: HistoryQueryAccess,
        matchId: Uuid,
        sessionId: Uuid?,
    ): HistoryManagementResult<HistoryArchiveStatusDto> = manage(sessionId) { activeDatabase, _ ->
        val id = matchId.toString()
        val snapshot = store.snapshot()
        val recording = snapshot.historyRecordingState
        val evidence = activeDatabase.readArchiveStatusEvidence(id)
        val queryPolicy = configState.current.history
        val activeGame = snapshot.games.values.firstOrNull { it.matchId == matchId }
        val recent = synchronized(recentMatchEvidence) { recentMatchEvidence[id] }
        val activeParticipant = activeGame?.tableState?.players?.any { it.id == access.principalId } == true
        val recentParticipant = access.principalId in (recent?.participantIds ?: emptySet())
        val authorized = access.isAdministrator &&
            queryPolicy.allowAdminQuery ||
            access.principalId.toString() in evidence.participantIds ||
            activeParticipant ||
            recentParticipant
        val pendingOutbox = recording.pendingEvents.any { it.matchId == matchId } ||
            matchId in recording.transfersByMatchId ||
            activeGame != null &&
            recording.decisionsByMatchId[matchId] == HistoryRecordingDecision.RECORDING
        evidence.toDto(
            decision = recording.decisionsByMatchId[matchId] ?: recent?.decision,
            pendingOutbox = pendingOutbox,
            queryEnabled = queryPolicy.queryEnabled,
            authorized = authorized,
            active = activeGame != null || matchId in recording.transfersByMatchId,
        )
    }

    /**
     * 取得目前仍能接續的對局，不因暫時缺少資料庫列而公開。
     *
     * @return 正式遊戲及隔離轉移中場次的識別碼。
     */
    private suspend fun excludedHistoryMatches(): Set<String> {
        val snapshot = store.snapshot()
        return snapshot.games.values.mapTo(mutableSetOf()) { it.matchId.toString() } +
            snapshot.historyRecordingState.transfersByMatchId.keys.map { it.toString() }
    }

    /**
     * 在同一 session、I/O dispatcher 及政策 lease 下執行一項有界管理工作。
     *
     * @param expectedSessionId 原要求捕捉的 session。
     * @param operation 型別化的資料庫管理工作，不得重入此入口。
     * @return 工作結果；例外只寫入內部 log，不傳送原始原因。
     */
    private suspend fun <T> manage(
        expectedSessionId: Uuid?,
        operation: suspend (SqliteHistoryDatabase, HistoryRetentionPolicy) -> T,
    ): HistoryManagementResult<T> {
        if (expectedSessionId == null && currentSessionId == null) return HistoryManagementResult.Disconnected(cachedStorageSnapshot)
        if (!isCurrentSession(expectedSessionId)) return HistoryManagementResult.SessionChanged
        if (!managementMutex.tryLock()) return HistoryManagementResult.Busy(cachedStorageSnapshot)
        val job = currentCoroutineContext()[Job]
        try {
            managementJob = job
            return withContext(dispatchers.io) {
                retentionCoordinator.withPolicy { policy ->
                    if (!isCurrentSession(expectedSessionId)) return@withPolicy HistoryManagementResult.SessionChanged
                    val activeDatabase = database.takeIf { connected } ?: return@withPolicy HistoryManagementResult.Disconnected(cachedStorageSnapshot)
                    val session = expectedSessionId ?: return@withPolicy HistoryManagementResult.Disconnected(cachedStorageSnapshot)
                    currentCoroutineContext().ensureActive()
                    val value = operation(activeDatabase, policy)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentSession(session)) HistoryManagementResult.SessionChanged else HistoryManagementResult.Success(value, session)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            lastError = error.message ?: error::class.simpleName
            logger.error("History management operation failed", error)
            return HistoryManagementResult.Failed((error as? HistoryManagementFailure)?.report, cachedStorageSnapshot)
        } finally {
            if (managementJob === job) managementJob = null
            managementMutex.unlock()
        }
    }

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
     * @param expectedSessionId 指令建立時的 session；等待鎖期間切換存檔時拒絕操作。
     * @return 重新連線、已連線或失敗結果。
     */
    suspend fun retry(server: MinecraftServer, expectedSessionId: Uuid? = currentSessionId): HistoryRetryResult = sessionMutex.withLock {
        if (!isCurrentSession(expectedSessionId)) return@withLock HistoryRetryResult.Failed
        if (currentSessionId == null) return@withLock HistoryRetryResult.Failed
        if (attachedServer != null && attachedServer !== server) return@withLock HistoryRetryResult.Failed
        if (connected) return@withLock HistoryRetryResult.AlreadyConnected
        worker?.cancelAndJoin()
        managementJob?.cancelAndJoin()
        worker = null
        retentionCoordinator.withPolicy {
            val transfers = store.snapshot().historyRecordingState.transfersByMatchId
            transfers.forEach { (id, transfer) ->
                store.finishHistoryTransfer(id, HistoryRecordingTerminal(Clock.System.now().toEpochMilliseconds(), false, transfer.venueId))
            }
        }
        cachedStorageSnapshot = null
        attemptedStoragePolicy = null
        lastError = null
        if (open(FabricHistoryDatabasePath.resolve(server))) HistoryRetryResult.Reconnected else HistoryRetryResult.Failed
    }

    /** 路徑版本供無 Minecraft server 的整合測試使用。
     *
     * @param path 歷史資料庫檔案位置。
     */
    internal suspend fun attach(path: Path) = attachSession(path, null)

    /**
     * 初始化固定路徑與 session 身分，不沿用前一存檔的用量或工作。
     *
     * @param path 目前存檔的固定資料庫位置。
     * @param server 正式 session 的平台實例；測試可為 null。
     */
    private suspend fun attachSession(path: Path, server: MinecraftServer?) = sessionMutex.withLock {
        check(currentSessionId == null && worker == null && database == null) { "History writer is already attached" }
        currentSessionId = Uuid.random()
        synchronized(recentMatchEvidence) { recentMatchEvidence.clear() }
        attachedServer = server
        cachedStorageSnapshot = null
        attemptedStoragePolicy = null
        startupStorageLogged = false
        storageRefreshMark = TimeSource.Monotonic.markNow()
        lastError = null
        knownGaps = emptyMap()
        synchronizedStops = emptyMap()
        archiveService.resetSession()
        store.applyHistoryStorageAvailability(true)
        retentionCoordinator.apply(configState.current.history.retentionPolicy())
        open(path)
    }

    /** 開啟、驗證並對帳；失敗時保留原資料與有效記錄政策，並暫停記錄新的歷史事件。
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
            logger.error("History database could not be opened; $PAUSED_RECORDING_CONSEQUENCE", error)
            store.applyHistoryStorageAvailability(false)
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
                    refreshStorageSafely(opened, policy)
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
            logger.error("History startup reconciliation failed; $PAUSED_RECORDING_CONSEQUENCE", error)
            store.applyHistoryStorageAvailability(false)
            return false
        }
        connected = true
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
                        if (attemptedStoragePolicy != policy || storageRefreshMark.elapsedNow() >= STORAGE_REFRESH_INTERVAL) {
                            refreshStorageSafely(opened, policy)
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
                    if (error is SQLException) {
                        logger.error("History database connection failed; $PAUSED_RECORDING_CONSEQUENCE", error)
                        connected = false
                        database = null
                        store.applyHistoryStorageAvailability(false)
                        return@launch
                    }
                    logger.error("History outbox write failed; pending events remain in the authoritative save", error)
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
        currentSessionId = null
        synchronized(recentMatchEvidence) { recentMatchEvidence.clear() }
        attachedServer = null
        worker?.cancelAndJoin()
        managementJob?.cancelAndJoin()
        worker = null
        retentionCoordinator.withPolicy {
            val transfers = store.snapshot().historyRecordingState.transfersByMatchId
            transfers.forEach { (id, transfer) ->
                store.finishHistoryTransfer(id, HistoryRecordingTerminal(Clock.System.now().toEpochMilliseconds(), false, transfer.venueId))
            }
        }
        cachedStorageSnapshot = null
        attemptedStoragePolicy = null
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
        attemptedStoragePolicy = null
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

    /**
     * 完整更新共用快照，成功後才輸出一次 session 摘要。
     *
     * @param activeDatabase 同一 session 的已驗證資料庫。
     * @param policy 查詢時固定的有效政策。
     * @return 新的用量快照。
     */
    private suspend fun refreshStorage(activeDatabase: SqliteHistoryDatabase, policy: HistoryRetentionPolicy): HistoryStorageSnapshot {
        val snapshot = storageQueryService.read(activeDatabase, policy)
        currentCoroutineContext().ensureActive()
        cachedStorageSnapshot = snapshot
        attemptedStoragePolicy = policy
        storageRefreshMark = TimeSource.Monotonic.markNow()
        if (!startupStorageLogged) {
            logger.info(
                "History storage initialized: completedMatches={}, activeMatches={}, partialMatches={}, unknownMatches={}, pendingSqlEvents={}, pendingOutboxEvents={}, dbBytes={}, walBytes={}, shmBytes={}, maxDiskBytes={}, maxMatches={}, retentionDuration={}, includeInterrupted={}",
                snapshot.completedMatchCount, snapshot.activeMatchCount, snapshot.partialMatchCount, snapshot.unknownMatchCount,
                snapshot.pendingSqlEventCount, snapshot.pendingOutboxEventCount, snapshot.disk.dbBytes, snapshot.disk.walBytes,
                snapshot.disk.shmBytes, policy.maxDiskBytes, policy.maxMatches, policy.retentionDuration, policy.includeInterruptedMatches,
            )
            startupStorageLogged = true
        }
        return snapshot
    }

    /**
     * 背景統計失敗只使快照維持舊狀態，不將用量改成零或停止正常寫入。
     *
     * @param activeDatabase 同一 session 的資料庫。
     * @param policy 固定的有效政策。
     */
    private suspend fun refreshStorageSafely(activeDatabase: SqliteHistoryDatabase, policy: HistoryRetentionPolicy) {
        try {
            refreshStorage(activeDatabase, policy)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            attemptedStoragePolicy = policy
            storageRefreshMark = TimeSource.Monotonic.markNow()
            lastError = error.message ?: error::class.simpleName
            logger.error("History storage statistics could not be refreshed", error)
        }
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
                HistoryTerminalRecord(id.toString(), terminal.venueId.toString(), terminal.endedAtEpochMillis, terminal.completed)
            },
        )
        val stopped = recording.decisionsByMatchId.filterValues {
            it == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED ||
                it == HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE ||
                it == HistoryRecordingDecision.STOPPED_TRANSFER_INTERRUPTED ||
                it == HistoryRecordingDecision.STOPPED_EXTERNALLY_MODIFIED
        }
        val stops = stopped.mapKeys { it.key.toString() }.mapValues { (_, decision) ->
            when (decision) {
                HistoryRecordingDecision.STOPPED_CONFIG_DISABLED -> PARTIAL_CONFIG_DISABLED
                HistoryRecordingDecision.STOPPED_TRANSFER_INTERRUPTED -> PARTIAL_TRANSFER_INTERRUPTED
                HistoryRecordingDecision.STOPPED_EXTERNALLY_MODIFIED -> PARTIAL_EXTERNALLY_MODIFIED
                else -> PARTIAL_STORAGE_UNAVAILABLE
            }
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
        /** 隔離權威來源未完成或無法繼續的部分紀錄診斷。 */
        const val PARTIAL_TRANSFER_INTERRUPTED: String = "PARTIAL_TRANSFER_INTERRUPTED"

        /** 設定停止的穩定資料庫診斷名稱，不代表完整 Replay。 */
        const val PARTIAL_CONFIG_DISABLED: String = "PARTIAL_CONFIG_DISABLED"

        /** 容量不足造成的部分紀錄診斷，不代表管理員停用總開關。 */
        const val PARTIAL_STORAGE_UNAVAILABLE: String = "PARTIAL_STORAGE_UNAVAILABLE"

        /** 對局被正常流程以外的方式修改後停止的部分紀錄診斷，之後的事件不對應正常的對局過程。 */
        const val PARTIAL_EXTERNALLY_MODIFIED: String = "PARTIAL_EXTERNALLY_MODIFIED"

        /** 資料庫無法使用而暫停記錄時，log 中說明對進行中場次與既有待寫事件的影響。 */
        const val PAUSED_RECORDING_CONSEQUENCE: String =
            "history recording is paused and active matches are marked incomplete; " +
                "already pending events stay in the world save until the database opens again"

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

        /** 用量統計的最大背景更新頻率，不每次事件提交重建快照。 */
        val STORAGE_REFRESH_INTERVAL = 30.seconds

        /** 正常關機時等待最後批次提交的時間上限。 */
        val SHUTDOWN_FLUSH_TIMEOUT = 5.seconds

        /** 即使沒有新事件，也定期評估到期政策及可回收空間。 */
        val MAINTENANCE_INTERVAL = 1.minutes

        /** 近期場次授權證據的有界數量。 */
        const val RECENT_MATCH_EVIDENCE_LIMIT = 256
    }
}

/**
 * 保留已提交刪除結果的內部管理失敗，不直接傳送給玩家。
 *
 * @property report 失敗前可證實的部分清理結果。
 * @param cause 原始內部例外，僅供 server log。
 */
private class HistoryManagementFailure(val report: HistoryCleanupReport, cause: Exception) : Exception("History cleanup did not finish", cause)
