package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryResultSummaryDto
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.sql.DriverManager
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import com.sun.management.OperatingSystemMXBean as ComSunOperatingSystemMXBean

/**
 * 可選的歷史查詢負載量測；預設略過，不讓一般品質門檻受機器效能影響。
 *
 * 以正式 SQLite 查詢、writer 的單一管理鎖與同一資料庫的待寫事件提交建立量測邊界。
 * 啟用方式為設定環境變數 `MAHJONGCRAFT_HISTORY_QUERY_LOAD_MEASUREMENT=true`。
 */
class HistoryQueryLoadMeasurementTest {
    /** 比較不同全伺服器准入上限下的查詢與待寫事件延遲。 */
    @Test
    fun `measure query admission caps against concurrent history writes`() = runBlocking {
        if (System.getenv(MEASUREMENT_ENVIRONMENT)?.toBoolean() != true) return@runBlocking

        val database = openFixture()
        val configurations = PLAYER_COUNTS.flatMap { playerCount ->
            LOAD_MODES.flatMap { mode -> CAP_VALUES.map { cap -> LoadConfiguration(cap, playerCount, mode) } }
        }
        repeat(WARMUP_ROUNDS) { warmupRound ->
            configurations.shuffled(Random(SHUFFLE_SEED + warmupRound)).forEach { configuration ->
                runLoad(database, configuration, repetition = -(warmupRound + 1), collect = false)
            }
        }
        val results = (1..REPETITIONS).flatMap { repetition ->
            configurations.shuffled(Random(SHUFFLE_SEED + WARMUP_ROUNDS + repetition)).map { configuration ->
                runLoad(database, configuration, repetition, collect = true)
            }
        }.sortedWith(compareBy(LoadMeasurement::cap, LoadMeasurement::playerCount, LoadMeasurement::mode, LoadMeasurement::repetition))
        results.forEach { result ->
            logger.info(
                "History query load measurement: cap={}, players={}, mode={}, repetition={}, completed={}, " +
                    "gateBusy={}, managementBusy={}, attempts={}, queryP50Ms={}, queryP95Ms={}, " +
                    "queryThroughputPerSecond={}, writes={}, writeP95Ms={}, processCpuMs={}, initialHeapBytes={}, " +
                    "peakHeapBytes={}, gcCollections={}",
                result.cap,
                result.playerCount,
                result.mode,
                result.repetition,
                result.completed,
                result.gateBusy,
                result.managementBusy,
                result.attempts,
                "%.3f".format(result.queryP50Millis),
                "%.3f".format(result.queryP95Millis),
                "%.3f".format(result.queryThroughputPerSecond),
                result.writeSamples,
                "%.3f".format(result.writeP95Millis),
                result.processCpuMillis,
                result.initialHeapBytes,
                result.peakHeapBytes,
                result.garbageCollections,
            )
        }
        val expectedStoredStates = EXPECTED_MATCH_COUNT +
            configurations.size * (WARMUP_ROUNDS + REPETITIONS) * WAVES * WRITE_OPERATIONS_PER_WAVE
        assertEquals(expectedStoredStates, database.readStatistics().matchStates.size)
        assertTrue(
            results.all { result ->
                result.attempts == result.playerCount * WAVES &&
                    result.completed + result.gateBusy + result.managementBusy == result.attempts
            },
        )
    }

    /**
     * 執行固定總工作量的一次暖機或正式量測。
     *
     * @param database 具有固定摘要資料的正式 SQLite 資料庫。
     * @param configuration 本次量測的准入上限、玩家數量與負載模式。
     * @param repetition 正式量測的重複編號；負值表示暖機。
     * @param collect 是否收集延遲樣本。
     * @return 正式量測的統計結果；暖機結果不應被使用。
     */
    private suspend fun runLoad(
        database: SqliteHistoryDatabase,
        configuration: LoadConfiguration,
        repetition: Int,
        collect: Boolean,
    ): LoadMeasurement {
        val executor = Executors.newSingleThreadExecutor()
        val serverDispatcher = executor.asCoroutineDispatcher()
        return try {
            withContext(serverDispatcher) {
                runLoadOnServer(database, configuration, repetition, collect)
            }
        } finally {
            serverDispatcher.close()
            executor.shutdownNow()
        }
    }

    /**
     * 在單一伺服器執行緒上送出固定玩家工作，並將資料庫工作交給背景協程。
     *
     * @param database 具有固定摘要資料的正式 SQLite 資料庫。
     * @param configuration 本次量測的准入上限、玩家數量與負載模式。
     * @param repetition 正式量測的重複編號；負值表示暖機。
     * @param collect 是否收集延遲與資源樣本。
     * @return 該組設定的量測結果。
     */
    private suspend fun runLoadOnServer(
        database: SqliteHistoryDatabase,
        configuration: LoadConfiguration,
        repetition: Int,
        collect: Boolean,
    ): LoadMeasurement = coroutineScope {
        val admission = HistoryQueryAdmission(limits = { HistoryQueryAdmissionLimits(maximumOutstanding = configuration.cap) })
        val managementLease = Mutex()
        val policyLease = Mutex()
        val samples = LoadSamples()
        val telemetry = RuntimeTelemetrySampler()
        val players = fixedPlayers(configuration.playerCount)
        val started = TimeSource.Monotonic.markNow()
        val writeJob = launch(Dispatchers.Default) {
            repeat(WAVES * WRITE_OPERATIONS_PER_WAVE) { operation ->
                val timer = TimeSource.Monotonic.markNow()
                policyLease.withLock {
                    database.appendPending(
                        PendingHistoryRecord(
                            matchId = "load-write-${configuration.cap}-${configuration.playerCount}-${configuration.mode.name}-$repetition-$operation",
                            sequence = 1L,
                            roundNumber = 1,
                            occurredAtEpochMillis = operation.toLong(),
                            payloadVersion = 1,
                            payload = "{}",
                        ),
                    )
                }
                samples.recordWrite(elapsedMillis(timer), collect)
                telemetry.sample()
                delay(WRITE_INTERVAL)
            }
        }

        repeat(WAVES) { wave ->
            val requests = mutableListOf<Deferred<Unit>>()
            players.forEachIndexed { index, player ->
                requests += async {
                    executeRequest(database, admission, managementLease, policyLease, player, samples, telemetry, collect)
                }
                if (configuration.mode == LoadMode.STAGGERED && index < players.lastIndex) delay(STAGGER_INTERVAL)
            }
            requests.awaitAll()
            if (wave < WAVES - 1) delay(MINIMUM_PLAYER_INTERVAL)
        }
        writeJob.join()
        val elapsed = elapsedMillis(started)
        val resources = telemetry.finish()
        LoadMeasurement(
            cap = configuration.cap,
            playerCount = configuration.playerCount,
            mode = configuration.mode,
            repetition = repetition,
            attempts = samples.attempts,
            gateBusy = samples.gateBusy,
            managementBusy = samples.managementBusy,
            completed = samples.completed,
            queryP50Millis = percentile(samples.queryMillis, 0.50),
            queryP95Millis = percentile(samples.queryMillis, 0.95),
            queryThroughputPerSecond = if (collect) samples.completed / (elapsed / 1_000.0) else 0.0,
            writeSamples = samples.writeCount,
            writeP95Millis = percentile(samples.writeMillis, 0.95),
            processCpuMillis = resources.processCpuMillis,
            initialHeapBytes = resources.initialHeapBytes,
            peakHeapBytes = resources.peakHeapBytes,
            garbageCollections = resources.garbageCollections,
        )
    }

    /**
     * 在背景協程執行一項查詢，並在伺服器執行緒完成回覆後才釋放准入權杖。
     *
     * @param database 正式 SQLite 資料庫。
     * @param admission 全伺服器查詢准入。
     * @param managementLease writer 管理 mutex 的測試模型。
     * @param policyLease 與待寫事件共享的政策 lease。
     * @param playerId 固定的查詢玩家 UUID。
     * @param samples 此次量測的統計累加器。
     * @param telemetry 執行期資源取樣器。
     * @param collect 是否收集成功查詢延遲。
     */
    private suspend fun executeRequest(
        database: SqliteHistoryDatabase,
        admission: HistoryQueryAdmission,
        managementLease: Mutex,
        policyLease: Mutex,
        playerId: Uuid,
        samples: LoadSamples,
        telemetry: RuntimeTelemetrySampler,
        collect: Boolean,
    ) {
        samples.attempts++
        val accepted = admission.acquire(playerId)
        if (accepted !is HistoryQueryAdmission.Admission.Accepted) {
            encodeErrorResponse(HistoryQueryErrorCodeDto.BUSY)
            samples.gateBusy++
            return
        }
        try {
            val timer = TimeSource.Monotonic.markNow()
            val page = withContext(Dispatchers.Default) {
                if (!managementLease.tryLock()) return@withContext null
                try {
                    policyLease.withLock {
                        database.readHistoryQueryPage(
                            SqliteHistoryQuery(
                                playerId = null,
                                includeAll = true,
                                sortField = SqliteHistorySortField.ENDED_AT,
                                sortDirection = SqliteHistorySortDirection.DESC,
                                pageSize = 20,
                            ),
                        )
                    }
                } finally {
                    managementLease.unlock()
                }
            }
            delay(SERVER_REPLY_DELAY)
            if (page != null) {
                Json.encodeToString(
                    HistoryListResponseDto.serializer(),
                    HistoryListResponseDto(requestId = "load-request", entries = page.entries.map { it.toResponseDto() }),
                )
                samples.completed++
                samples.recordQuery(elapsedMillis(timer), collect)
            } else {
                encodeErrorResponse(HistoryQueryErrorCodeDto.BUSY)
                samples.managementBusy++
            }
            telemetry.sample()
        } finally {
            admission.release(playerId, accepted.token)
        }
    }

    /** 將忙碌回覆序列化，計入正式回覆的配置與 CPU 成本。
     *
     * @param errorCode 要回覆的穩定錯誤代碼。
     */
    private fun encodeErrorResponse(errorCode: HistoryQueryErrorCodeDto) {
        Json.encodeToString(
            HistoryListResponseDto.serializer(),
            HistoryListResponseDto(requestId = "load-request", entries = emptyList(), errorCode = errorCode),
        )
    }

    /** 將正式 SQLite 摘要轉成實際歷史清單網路 DTO。
     *
     * @return 包含參與者與終局結果的線路摘要。
     */
    private fun SqliteHistoryQueryEntry.toResponseDto(): HistoryMatchSummaryDto = HistoryMatchSummaryDto(
        matchId = matchId,
        ruleId = ruleId,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        outcome = outcome.toDto(),
        integrity = state.toDto(),
        durationMillis = durationMillis,
        participants = participants.map { HistoryParticipantSummaryDto(it.seatIndex, it.playerId, it.aiStrategyId) },
        roundCount = rounds.size.takeIf { state == HistoryStoredMatchState.COMPLETED },
        resultsAvailable = state == HistoryStoredMatchState.COMPLETED,
        results = participants.map { HistoryResultSummaryDto(it.playerId, it.finalScore, it.finalRank) },
    )

    /** 將資料庫終局狀態轉為網路列舉。
     *
     * @return 對應的線路終局狀態。
     */
    private fun SqliteHistoryMatchOutcome.toDto(): HistoryOutcomeFilterDto = when (this) {
        SqliteHistoryMatchOutcome.COMPLETED -> HistoryOutcomeFilterDto.COMPLETED
        SqliteHistoryMatchOutcome.INTERRUPTED -> HistoryOutcomeFilterDto.INTERRUPTED
    }

    /** 將資料庫完整性狀態轉為網路列舉。
     *
     * @return 對應的線路完整性狀態。
     */
    private fun HistoryStoredMatchState.toDto(): HistoryIntegrityFilterDto = when (this) {
        HistoryStoredMatchState.COMPLETED -> HistoryIntegrityFilterDto.COMPLETE
        else -> HistoryIntegrityFilterDto.INCOMPLETE
    }

    /** 建立每位玩家跨負載波次固定不變的 UUID。
     *
     * @param count 固定玩家數量。
     * @return 不重複且跨波次穩定的玩家識別碼。
     */
    private fun fixedPlayers(count: Int): List<Uuid> = (0 until count).map { index ->
        Uuid.parse("00000000-0000-0000-0000-${index.toString(16).padStart(12, '0')}")
    }

    /** 同步突發與正常錯開兩種輸入模式。 */
    private enum class LoadMode {
        /** 同一伺服器 tick 內提交所有玩家要求。 */
        BURST,

        /** 以短間隔逐一提交玩家要求。 */
        STAGGERED,
    }

    /**
     * 一組固定的准入與玩家負載設定。
     *
     * @property cap 全伺服器查詢准入上限。
     * @property playerCount 此輪要求的固定玩家數量。
     * @property mode 輸入提交模式。
     */
    private data class LoadConfiguration(
        val cap: Int,
        val playerCount: Int,
        val mode: LoadMode,
    )

    /** 一輪負載量測中的計數與成功延遲樣本。 */
    private class LoadSamples {
        /** 所有送出的查詢嘗試數。 */
        var attempts: Int = 0

        /** 被全伺服器准入拒絕的數量。 */
        var gateBusy: Int = 0

        /** 被管理 mutex 拒絕的數量。 */
        var managementBusy: Int = 0

        /** 完成 SQL 與 DTO 序列化的數量。 */
        var completed: Int = 0

        /** 成功查詢的小數毫秒延遲。 */
        val queryMillis: MutableList<Double> = mutableListOf()

        /** 成功待寫事件的小數毫秒延遲。 */
        val writeMillis: MutableList<Double> = mutableListOf()

        /** 已完成待寫事件數量。 */
        var writeCount: Int = 0

        /** 記錄成功查詢延遲。
         *
         * @param value 本次延遲的毫秒數。
         * @param collect 是否保存延遲樣本。
         */
        fun recordQuery(value: Double, collect: Boolean) {
            if (collect) queryMillis += value
        }

        /** 記錄待寫事件延遲。
         *
         * @param value 本次延遲的毫秒數。
         * @param collect 是否保存延遲樣本。
         */
        fun recordWrite(value: Double, collect: Boolean) {
            writeCount++
            if (collect) writeMillis += value
        }
    }

    /** JVM 資源指標取樣器；不支援的指標保留 null。 */
    private class RuntimeTelemetrySampler {
        /** 量測開始時觀察到的 heap 使用量。 */
        private var initialHeapBytes: Long? = null

        /** 量測期間觀察到的最大 heap 使用量。 */
        private var peakHeapBytes: Long? = null

        /** 量測開始時的處理程序 CPU 時間。 */
        private val initialProcessCpuNanos = processCpuNanos()

        /** 量測開始時的 GC 累積次數。 */
        private val initialGarbageCollections = garbageCollectionCount()

        init {
            sample()
        }

        /** 更新 heap 峰值。 */
        @Synchronized
        fun sample() {
            val used = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
            if (used >= 0) {
                initialHeapBytes = initialHeapBytes ?: used
                peakHeapBytes = maxOf(peakHeapBytes ?: used, used)
            }
        }

        /** 取得處理程序 CPU、heap 與 GC 的量測結果。
         *
         * @return 本輪資源統計；不支援的指標為 null。
         */
        fun finish(): RuntimeTelemetry {
            val processCpuMillis = processCpuNanos()?.let { current ->
                initialProcessCpuNanos?.let { (current - it).coerceAtLeast(0L) / 1_000_000 }
            }
            val garbageCollections = garbageCollectionCount()?.let { current ->
                initialGarbageCollections?.let { (current - it).coerceAtLeast(0L) }
            }
            return RuntimeTelemetry(processCpuMillis, initialHeapBytes, peakHeapBytes, garbageCollections)
        }

        /** 取得支援時的處理程序 CPU 奈秒數。
         *
         * @return 處理程序累積 CPU 奈秒數；不支援時為 null。
         */
        private fun processCpuNanos(): Long? = (ManagementFactory.getOperatingSystemMXBean() as? ComSunOperatingSystemMXBean)
            ?.processCpuTime
            ?.takeIf { it >= 0 }

        /** 取得支援時的 GC 累積次數。
         *
         * @return 所有 GC 的累積次數；不支援時為 null。
         */
        private fun garbageCollectionCount(): Long? = ManagementFactory.getGarbageCollectorMXBeans()
            .map { it.collectionCount }
            .takeIf { it.isNotEmpty() && it.none { count -> count < 0 } }
            ?.sum()
    }

    /**
     * JVM 資源量測結果。
     *
     * @property processCpuMillis 本輪處理程序 CPU 時間；不支援時為 null。
     * @property initialHeapBytes 本輪開始取樣到的 heap 使用量；不支援時為 null。
     * @property peakHeapBytes 本輪取樣到的最大 heap 使用量；不支援時為 null。
     * @property garbageCollections 本 JVM 本輪 GC 次數增量；不支援時為 null。
     */
    private data class RuntimeTelemetry(
        val processCpuMillis: Long?,
        val initialHeapBytes: Long?,
        val peakHeapBytes: Long?,
        val garbageCollections: Long?,
    )

    /**
     * 將單調計時轉為小數毫秒，保留短查詢的差異。
     *
     * @param mark 已開始的單調計時。
     * @return 經過的毫秒數。
     */
    private fun elapsedMillis(mark: TimeMark): Double = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0

    /**
     * 建立至少一千筆可查詢的摘要資料。
     *
     * @return 已開啟的 SQLite 資料庫。
     */
    private fun openFixture(): SqliteHistoryDatabase {
        val path = createTempDirectory("mahjongcraft-history-query-load-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.autoCommit = false
            connection.prepareStatement(
                "INSERT INTO history_match(match_id, table_id, rule_id, dimension_id, status, started_at_epoch_millis, ended_at_epoch_millis) " +
                    "VALUES (?, ?, ?, NULL, 'COMPLETED', ?, ?)",
            ).use { match ->
                connection.prepareStatement(
                    "INSERT INTO history_replay(match_id, format_version, created_at_epoch_millis, payload) VALUES (?, 1, ?, '{}')",
                ).use { replay ->
                    connection.prepareStatement(
                        "INSERT INTO history_participant(match_id, seat_index, player_id, ai_strategy_id) VALUES (?, ?, ?, NULL)",
                    ).use { participant ->
                        repeat(EXPECTED_MATCH_COUNT) { index ->
                            val id = "load-match-$index"
                            match.setString(1, id)
                            match.setString(2, "table-$index")
                            match.setString(3, "mahjongcraft:riichi")
                            match.setLong(4, index.toLong())
                            match.setLong(5, (index + 1).toLong())
                            match.addBatch()
                            replay.setString(1, id)
                            replay.setLong(2, (index + 1).toLong())
                            replay.addBatch()
                            repeat(PARTICIPANTS_PER_MATCH) { seat ->
                                participant.setString(1, id)
                                participant.setInt(2, seat)
                                participant.setString(3, "00000000-0000-0000-0000-${(index * PARTICIPANTS_PER_MATCH + seat).toString(16).padStart(12, '0')}")
                                participant.addBatch()
                            }
                        }
                        match.executeBatch()
                        replay.executeBatch()
                        participant.executeBatch()
                    }
                }
            }
            connection.prepareStatement(
                "INSERT INTO history_participant_result(match_id, seat_index, final_score, final_rank) VALUES (?, ?, ?, ?)",
            ).use { result ->
                repeat(EXPECTED_MATCH_COUNT) { index ->
                    repeat(PARTICIPANTS_PER_MATCH) { seat ->
                        result.setString(1, "load-match-$index")
                        result.setInt(2, seat)
                        result.setInt(3, 40_000 - seat * 10_000)
                        result.setInt(4, seat + 1)
                        result.addBatch()
                    }
                }
                result.executeBatch()
            }
            connection.commit()
        }
        return database
    }

    /**
     * 取排序後的近似百分位，量測僅供比較，不作為穩定單元測試斷言。
     *
     * @param values 已完成操作的小數毫秒延遲。
     * @param fraction 百分位的 0～1 小數。
     * @return 對應百分位的延遲，空集合為零。
     */
    private fun percentile(values: List<Double>, fraction: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((sorted.lastIndex * fraction).toInt()).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    /**
     * 單次負載量測的統計結果。
     *
     * @property cap 該次測試使用的全伺服器准入上限。
     * @property playerCount 該次固定發出要求的玩家數量。
     * @property mode 該次使用的輸入提交模式。
     * @property repetition 該上限的正式重複編號；暖機不會建立此結果。
     * @property attempts 固定送出的查詢嘗試數。
     * @property gateBusy 被全伺服器准入層拒絕的嘗試數。
     * @property managementBusy 取得准入後被 writer 管理鎖拒絕的嘗試數。
     * @property completed 成功完成資料庫查詢的嘗試數。
     * @property queryP50Millis 查詢延遲近似第 50 百分位，單位為毫秒。
     * @property queryP95Millis 查詢延遲近似第 95 百分位，單位為毫秒。
     * @property queryThroughputPerSecond 每秒完成的查詢數量近似值。
     * @property writeSamples 已收集的待寫事件提交延遲樣本數。
     * @property writeP95Millis 待寫事件提交延遲近似第 95 百分位，單位為毫秒。
     * @property processCpuMillis 本輪處理程序 CPU 時間；不支援時為 null。
     * @property initialHeapBytes 本輪起始 heap 使用量；不支援時為 null。
     * @property peakHeapBytes 本輪取樣到的最大 heap 使用量；不支援時為 null。
     * @property garbageCollections 本 JVM GC 累積次數；不支援時為 null。
     */
    private data class LoadMeasurement(
        val cap: Int,
        val playerCount: Int,
        val mode: LoadMode,
        val repetition: Int,
        val attempts: Int,
        val gateBusy: Int,
        val managementBusy: Int,
        val completed: Int,
        val queryP50Millis: Double,
        val queryP95Millis: Double,
        val queryThroughputPerSecond: Double,
        val writeSamples: Int,
        val writeP95Millis: Double,
        val processCpuMillis: Long?,
        val initialHeapBytes: Long?,
        val peakHeapBytes: Long?,
        val garbageCollections: Long?,
    )

    /** 量測固定參數與 logger。 */
    private companion object {
        /** 啟用選擇性量測的環境變數名稱。 */
        private const val MEASUREMENT_ENVIRONMENT = "MAHJONGCRAFT_HISTORY_QUERY_LOAD_MEASUREMENT"

        /** 建立的完整摘要場次數量。 */
        private const val EXPECTED_MATCH_COUNT = 1_000

        /** 正式量測的重複次數。 */
        private const val REPETITIONS = 3

        /** 每個負載波次與查詢競爭的循序待寫事件數量。 */
        private const val WRITE_OPERATIONS_PER_WAVE = 8

        /** 每組設定的暖機輪數。 */
        private const val WARMUP_ROUNDS = 1

        /** 每輪要求的波次數量。 */
        private const val WAVES = 2

        /** 輸入玩家數量組合。 */
        private val PLAYER_COUNTS = listOf(16, 32, 64)

        /** 輸入負載模式。 */
        private val LOAD_MODES = LoadMode.entries

        /** 同一玩家跨波次要求之間的最短等待。 */
        private val MINIMUM_PLAYER_INTERVAL = 260.milliseconds

        /** 正常錯開模式的提交間隔。 */
        private val STAGGER_INTERVAL = 10.milliseconds

        /** writer 循序提交事件之間的穩定間隔。 */
        private val WRITE_INTERVAL = 25.milliseconds

        /** 模擬單一伺服器 tick 回覆所需的等待，不是查詢取消逾時。 */
        private val SERVER_REPLY_DELAY = 50.milliseconds

        /** 每筆 fixture 對局的穩定參與者數量。 */
        private const val PARTICIPANTS_PER_MATCH = 4

        /** 固定 sender 與 cap 順序洗牌的種子。 */
        private const val SHUFFLE_SEED = 0x5EED

        /** 比較的伺服器准入上限。 */
        private val CAP_VALUES = listOf(8, 16, 32, 64)

        /** 量測結果 logger。 */
        private val logger = LoggerFactory.getLogger(HistoryQueryLoadMeasurementTest::class.java)
    }
}
