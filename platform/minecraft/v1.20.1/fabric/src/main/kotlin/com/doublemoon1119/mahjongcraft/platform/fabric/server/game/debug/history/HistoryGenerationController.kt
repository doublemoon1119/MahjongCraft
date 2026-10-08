package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTransferResult
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessFlowHistoryRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRunner
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryProgress
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryGenerationReceipt
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryManagementResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** 為生成工作建立一次性的隔離 Flow 環境，不使用正式玩家集合。 */
fun interface HistoryGenerationRuntimeFactory {
    /**
     * 建立採用指定規則與場長的全 AI 對局。
     * @param scenario 場長情境。
     * @return 已走正式開局流程的隔離環境。
     */
    suspend fun create(scenario: HeadlessHistoryScenario): HeadlessHistoryMatchRuntime
}

/**
 * 使用 extension 初始化完成後的 registry 建立隔離生成環境，讓生成的對局與正式對局使用同一套規則登記。
 *
 * @property moduleRegistry 規則模組。
 * @property aiStrategies AI 策略。
 * @property winCelebrationCueResolvers 胡牌演出提示。
 * @property postActionExhaustiveDrawResolvers 動作後的途中流局判定。
 * @property postReactionRoundOutcomeResolvers 回應結束後的本局結果判定。
 * @property winRoundContinuationResolvers 胡牌後本局是否繼續。
 * @property winSettlementDetailResolvers 胡牌詳情。
 * @property gameCommands 擴充命令 handler。
 */
@Single
class FabricHistoryGenerationRuntimeFactory(
    @Provided private val moduleRegistry: MahjongModuleRegistry,
    @Provided private val aiStrategies: MahjongAiStrategyRegistry,
    @Provided private val winCelebrationCueResolvers: WinCelebrationCueResolverRegistry,
    @Provided private val postActionExhaustiveDrawResolvers: PostActionExhaustiveDrawResolverRegistry,
    @Provided private val postReactionRoundOutcomeResolvers: PostReactionRoundOutcomeResolverRegistry,
    @Provided private val winRoundContinuationResolvers: WinRoundContinuationResolverRegistry,
    @Provided private val winSettlementDetailResolvers: WinSettlementDetailResolverRegistry,
    @Provided private val gameCommands: ExtensionGameCommandExecutorRegistry,
) : HistoryGenerationRuntimeFactory {
    /**
     * 建立四個真 AI 座位的完整對局。
     * @param scenario 場長情境。
     * @return 不產生世界實體的隔離環境。
     */
    override suspend fun create(scenario: HeadlessHistoryScenario): HeadlessHistoryMatchRuntime = HeadlessFlowHistoryRuntime.create(
        scenario = scenario,
        registries = HeadlessHistoryRegistries(
            moduleRegistry = moduleRegistry,
            aiStrategyRegistry = aiStrategies,
            winCelebrationCueResolverRegistry = winCelebrationCueResolvers,
            postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolvers,
            postReactionRoundOutcomeResolverRegistry = postReactionRoundOutcomeResolvers,
            winRoundContinuationResolverRegistry = winRoundContinuationResolvers,
            winSettlementDetailResolverRegistry = winSettlementDetailResolvers,
            gameCommandRegistry = gameCommands,
        ),
    )
}

/** 開始生成的安全結果，不含 SQL 或原始例外。 */
sealed interface HistoryGenerationStartResult {
    /** 已啟動一批工作。 */
    data object Started : HistoryGenerationStartResult

    /** 原有批次仍執行中。 */
    data object Busy : HistoryGenerationStartResult

    /** 不支援指定情境。 */
    data object InvalidScenario : HistoryGenerationStartResult

    /** 尚未綁定存檔 session。 */
    data object SessionUnavailable : HistoryGenerationStartResult

    /** 總開關或 AI 篩選拒絕新場。 */
    data object PolicyRejected : HistoryGenerationStartResult

    /** writer 斷線或容量已停止。 */
    data object StorageUnavailable : HistoryGenerationStartResult
}

/**
 * 管理單一存檔的有界歷史生成批次；來源僅在 SQLite 寫入確認後清除事件。
 * @property writer 唯一正式 outbox writer 與 session 邊界。
 * @property runtimeFactory 隔離 Flow 組裝入口。
 * @property store 正式待寫佇列，僅讀取提交確認與固定記錄決策。
 * @property configState 開始前檢查的有效 server 設定。
 * @property scope 隨伺服器 session 取消的應用作用域。
 * @property dispatchers 背景對局計算使用的調度器。
 */
@Single
class HistoryGenerationController(
    private val writer: FabricHistoryOutboxWriter,
    private val runtimeFactory: HistoryGenerationRuntimeFactory,
    private val store: AuthoritativeStateStore,
    @Provided private val configState: MinecraftServerConfigState,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 安全的生成診斷，不輸出玩家或牌面。 */
    private val logger = mahjongCraftLogger(HistoryGenerationController::class)

    /** 原子發布的最新進度。 */
    private val state = MutableStateFlow<HistoryGenerationProgress?>(null)

    /** 保護並發開始要求，避免建立多批工作。 */
    private val startMutex = Mutex()

    /** 場間取消要求，不取消當前對局。 */
    private val cancellationRequested = AtomicBoolean(false)

    /** 目前或已完成的工作。 */
    @Volatile private var job: Job? = null

    /** 進度所屬的固定 session，切換後不對外顯示。 */
    @Volatile private var sessionId: Uuid? = null

    /**
     * 驗證政策及 session 後啟動一批工作。
     * @param scenarioId 場長情境 ID。
     * @param count 要求場數，限制為 1 至 100。
     * @param expectedSessionId 要求發出時所屬的存檔 session。
     * @return 開始或拒絕的安全原因。
     */
    suspend fun start(scenarioId: String, count: Int, expectedSessionId: Uuid? = writer.currentSessionId): HistoryGenerationStartResult = startMutex.withLock {
        require(count in 1..MAX_MATCHES) { "History generation count must be between 1 and 100" }
        val scenario = HeadlessHistoryScenario.entries.firstOrNull { it.identifier == scenarioId }
            ?: return@withLock HistoryGenerationStartResult.InvalidScenario
        val session = writer.currentSessionId ?: return@withLock HistoryGenerationStartResult.SessionUnavailable
        if (expectedSessionId != session) return@withLock HistoryGenerationStartResult.SessionUnavailable
        if (job?.isActive == true) {
            if (sessionId == session) return@withLock HistoryGenerationStartResult.Busy
            job?.cancelAndJoin()
        }
        val policy = configState.current.history
        if (!policy.enabled || !policy.includeAiMatches) return@withLock HistoryGenerationStartResult.PolicyRejected
        val status = writer.status()
        if (!status.databaseConnected || status.storagePaused) return@withLock HistoryGenerationStartResult.StorageUnavailable
        if (!writer.isCurrentSession(session)) return@withLock HistoryGenerationStartResult.SessionUnavailable
        scope.coroutineContext.ensureActive()
        cancellationRequested.set(false)
        sessionId = session
        state.value = HistoryGenerationProgress(scenario.identifier, count)
        job = scope.launch(dispatchers.default, start = CoroutineStart.LAZY) { runBatch(scenario, count, session) }
        job?.start()
        HistoryGenerationStartResult.Started
    }

    /**
     * 要求目前場次完成後停止，不回滾已提交事件。
     * @return 是否存在目前 session 的執行中批次。
     */
    internal fun cancel(): Boolean {
        if (!writer.isCurrentSession(sessionId) || job?.isActive != true) return false
        cancellationRequested.set(true)
        state.update { it?.copy(cancelRequested = true) }
        return true
    }

    /**
     * 讀取目前 session 的最新安全進度。
     * @return 最新進度，或沒有同 session 批次時的 null。
     */
    internal fun snapshot(): HistoryGenerationProgress? = state.value.takeIf { writer.isCurrentSession(sessionId) }

    /**
     * 在批次時間限制內逐場計算及封存。
     * @param scenario 場長情境。
     * @param count 要求場數。
     * @param session 開始時固定的存檔 session。
     */
    private suspend fun runBatch(scenario: HeadlessHistoryScenario, count: Int, session: Uuid) {
        val started = TimeSource.Monotonic.markNow()
        var activeRuntime: HeadlessHistoryMatchRuntime? = null
        val completed = mutableSetOf<Uuid>()
        val archived = mutableSetOf<Uuid>()
        val pruned = mutableSetOf<Uuid>()
        val samples = mutableMapOf<Uuid, Long>()
        try {
            withTimeout(BATCH_TIMEOUT) {
                val before = managed(session) { writer.generationReceipt(emptySet(), session) }
                state.update { it?.copy(diskBefore = before.disk, diskAfter = before.disk) }
                repeat(count) {
                    if (cancellationRequested.get()) return@withTimeout
                    requireSession(session)
                    activeRuntime = withTimeout(MATCH_TIMEOUT) { runtimeFactory.create(scenario) }
                    val runtime = checkNotNull(activeRuntime)
                    val game = checkNotNull(runtime.currentGame()) { "Generated match has no opening game" }
                    if (!managed(session) { writer.beginGeneration(game, session) }) {
                        val policy = configState.current.history
                        val failure = if (!policy.enabled || !policy.includeAiMatches) HistoryGenerationFailure.POLICY else HistoryGenerationFailure.STORAGE
                        throw GenerationStopped(failure)
                    }
                    state.update { it?.copy(pending = 1, elapsed = started.elapsedNow()) }
                    withTimeout(MATCH_TIMEOUT) {
                        HeadlessHistoryMatchRunner(runtime).events().collect { batch ->
                            requireSession(session)
                            if (batch.events.isNotEmpty()) {
                                submitAndConfirm(batch, session)
                                runtime.store.acknowledgeHistoryEvents(batch.events.mapTo(mutableSetOf()) { it.matchId to it.sequence })
                            } else if (batch.terminal) {
                                val terminal = checkNotNull(batch.snapshot.historyRecordingState.terminalByMatchId[batch.matchId]) {
                                    "Generated match has no authoritative terminal evidence"
                                }
                                check(terminal.completed) { "Generated match did not complete normally" }
                                completed += batch.matchId
                                val receipt = managed(session) { writer.finishGeneration(batch.matchId, terminal, session) }
                                recordReceipt(receipt, archived, pruned, samples)
                                check(batch.matchId in archived || batch.matchId in pruned) { "Generated match has no archive receipt" }
                            }
                            state.update { it?.copy(elapsed = started.elapsedNow()) }
                        }
                    }
                    activeRuntime = null
                    val receipt = managed(session) { writer.generationReceipt(completed, session) }
                    recordReceipt(receipt, archived, pruned, samples)
                    state.update {
                        it?.copy(
                            generated = completed.size, archived = archived.size, pruned = pruned.size,
                            pending = (completed - archived - pruned).size, diskAfter = receipt.disk,
                            replayBytes = samples.values.sum(), largestReplayBytes = samples.values.maxOrNull() ?: 0L,
                            replaySampleCount = samples.size, elapsed = started.elapsedNow(),
                        )
                    }
                }
            }
            logger.info("History generation batch finished: generated={}, archived={}, pruned={}, replayBytes={}", completed.size, archived.size, pruned.size, samples.values.sum())
        } catch (timeout: TimeoutCancellationException) {
            markFailure(HistoryGenerationFailure.TIMEOUT)
            logger.warn("History generation batch exceeded its time limit")
        } catch (cancelled: CancellationException) {
            markFailure(HistoryGenerationFailure.SESSION)
            throw cancelled
        } catch (stopped: GenerationStopped) {
            markFailure(stopped.failure)
            logger.warn("History generation batch stopped: reason={}", stopped.failure)
        } catch (error: Exception) {
            markFailure(HistoryGenerationFailure.VALIDATION)
            logger.error("History generation batch failed validation", error)
        } finally {
            withContext(NonCancellable) {
                val runtime = activeRuntime
                if (runtime != null) {
                    withTimeoutOrNull(WRITER_TIMEOUT) {
                        writer.abortGeneration(runtime.matchId(), HistoryRecordingTerminal(Clock.System.now().toEpochMilliseconds(), false, runtime.venueId), session)
                    }
                }
                state.update {
                    it?.copy(
                        running = false,
                        elapsed = started.elapsedNow(),
                        generated = completed.size,
                        archived = archived.size,
                        pruned = pruned.size,
                        replayBytes = samples.values.sum(),
                        largestReplayBytes = samples.values.maxOrNull() ?: 0L,
                        replaySampleCount = samples.size,
                    )
                }
            }
        }
    }

    /**
     * 追加完整批次，等待正式 outbox 的精確 SQLite 確認後才返回。
     * @param batch 一步產生的完整交易批次。
     * @param session 固定存檔 session。
     */
    private suspend fun submitAndConfirm(batch: HeadlessHistoryProgress, session: Uuid) = withTimeout(WRITER_TIMEOUT) {
        val ids = batch.events.mapTo(mutableSetOf()) { it.matchId to it.sequence }
        while (true) {
            when (managed(session) { writer.appendGeneration(batch.matchId, batch.events, session) }) {
                HistoryTransferResult.STOPPED -> throw GenerationStopped(recordingFailure(batch.matchId))
                HistoryTransferResult.WAITING_FOR_CAPACITY -> delay(RETRY_INTERVAL)
                HistoryTransferResult.ACCEPTED -> break
            }
        }
        while (true) {
            requireSession(session)
            val recording = store.snapshot().historyRecordingState
            if (recording.decisionsByMatchId[batch.matchId] != HistoryRecordingDecision.RECORDING) {
                throw GenerationStopped(recordingFailure(batch.matchId))
            }
            if (recording.pendingEvents.none { (it.matchId to it.sequence) in ids }) break
            delay(RETRY_INTERVAL)
        }
    }

    /**
     * 將權威的固定停止決策轉為批次回報原因。
     * @param matchId 被停止的生成場次。
     * @return 設定停止或儲存停止分類。
     */
    private suspend fun recordingFailure(matchId: Uuid): HistoryGenerationFailure = if (store.snapshot().historyRecordingState.decisionsByMatchId[matchId] == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED) {
        HistoryGenerationFailure.POLICY
    } else {
        HistoryGenerationFailure.STORAGE
    }

    /**
     * 重試忙碌要求，其他管理失敗明確停止；不自行重新開庫。
     * @param session 固定存檔 session。
     * @param operation 唯一 writer 的型別化工作。
     * @return 工作成功值。
     */
    private suspend fun <T> managed(session: Uuid, operation: suspend () -> HistoryManagementResult<T>): T = withTimeout(WRITER_TIMEOUT) {
        while (true) {
            requireSession(session)
            when (val result = operation()) {
                is HistoryManagementResult.Success -> return@withTimeout result.value
                is HistoryManagementResult.Busy -> delay(RETRY_INTERVAL)
                HistoryManagementResult.SessionChanged -> throw GenerationStopped(HistoryGenerationFailure.SESSION)
                is HistoryManagementResult.Disconnected -> throw GenerationStopped(HistoryGenerationFailure.STORAGE)
                is HistoryManagementResult.Failed -> throw GenerationStopped(HistoryGenerationFailure.STORAGE)
            }
        }
        @Suppress("UNREACHABLE_CODE") // 迴圈只會成功返回或拋出明確停止原因。
        error("History management retry loop exited unexpectedly")
    }

    /**
     * 合併收據，不因重複查詢重複計算場次或 Replay bytes。
     * @param receipt SQLite 已提交的封存及清理證據。
     * @param archived 已確認封存的累積集合。
     * @param pruned 已確認清理的累積集合。
     * @param samples 已量測 payload 的累積對照。
     */
    private fun recordReceipt(receipt: HistoryGenerationReceipt, archived: MutableSet<Uuid>, pruned: MutableSet<Uuid>, samples: MutableMap<Uuid, Long>) {
        receipt.replayBytes.forEach { (id, bytes) ->
            val matchId = Uuid.parse(id)
            archived += matchId
            samples[matchId] = bytes
        }
        receipt.pruned.forEach { pruned += Uuid.parse(it) }
    }

    /**
     * 驗證來源仍屬於開始時的存檔。
     * @param session 開始時的 session。
     */
    private fun requireSession(session: Uuid) {
        if (!writer.isCurrentSession(session)) throw GenerationStopped(HistoryGenerationFailure.SESSION)
    }

    /**
     * 保留失敗分類與失敗場數，原始訊息不傳給玩家。
     * @param failure 安全分類。
     */
    private fun markFailure(failure: HistoryGenerationFailure) {
        state.update { it?.copy(failure = failure, failed = it.failed + 1) }
    }

    /**
     * 具型別的安全停止，不從例外字串猜測原因。
     * @property failure 停止分類。
     */
    private class GenerationStopped(val failure: HistoryGenerationFailure) : IllegalStateException("History generation stopped: $failure")

    private companion object {
        /** 單批場數上限。 */
        const val MAX_MATCHES: Int = 100

        /** 單場的最大運算時間。 */
        val MATCH_TIMEOUT = 5.minutes

        /** 整批的最大運算時間。 */
        val BATCH_TIMEOUT = 30.minutes

        /** 寫入及收尾的有界等待時間。 */
        val WRITER_TIMEOUT = 60.seconds

        /** 等待 busy 或容量背壓的重試間隔。 */
        val RETRY_INTERVAL = 25.milliseconds
    }
}
