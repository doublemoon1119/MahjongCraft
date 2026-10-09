package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

/** 平台在每次自動推進的最後補做的檢查，例如替真人玩家自動摸牌。 */
fun interface AutomatedAdvanceFollowUp {
    /**
     * 推進 [gameId] 後補做平台的檢查。
     *
     * @param gameId 剛推進的遊戲。
     * @return 是否改變了遊戲；改變時 [AutomatedAdvanceManager] 會再推進一輪。
     */
    suspend fun afterAdvance(gameId: Uuid): Boolean
}

/** 接收自動推進失敗的情況，供平台記錄。 */
fun interface AutomatedAdvanceFailureReporter {
    /**
     * 推進 [gameId] 時發生未預期的錯誤；這次推進已停止，之後的請求照常推進。
     *
     * @param gameId 推進失敗的遊戲。
     * @param error 推進時丟出的錯誤。
     */
    fun onAdvanceFailed(gameId: Uuid, error: Exception)
}

/**
 * 每一局同一時間只有一個自動推進：所有驅動自動操作的入口都向這裡請求，不直接驅動。
 *
 * - [request]：這一局已在推進時，只標記「需要再推進一輪」並立即返回；否則登記這一局、在 [scope] 上以 [dispatcher] 啟動推進
 *   協程後立即返回，不等待推進完成。推進協程每一輪結束時檢查標記，有標記就再推進一輪，因此不會漏掉推進期間的請求。
 * - [runExclusive]：在呼叫端協程中執行一段推進（例如逐步推進的離線對局）；這一局已在推進時略過。執行期間收到的 [request]
 *   在它結束後才推進。
 *
 * 登記在啟動協程之前完成，避免協程尚未開始時重複排入。登記的釋放不依賴推進本文：協程建立時立即登記完成回呼，由回呼依登記的
 * 識別碼清除，所以協程在開始前就被取消時也會釋放；清除以識別碼比對，重複清除不會影響之後的登記。一局推進失敗時交給
 * [failureReporter] 並停止這次推進，不影響其他局；取消會照常往外傳。[startSession] 清空所有登記，舊 session 的協程結束時
 * 因識別碼不同，不會清掉新 session 的登記。完成回呼可能在任何執行緒上觸發，登記以原子操作保護。
 *
 * @property scope 啟動推進協程的作用域，通常是目前 server session 的作用域。
 * @property dispatcher 推進協程使用的調度器，通常是伺服器主執行緒。
 * @property advanceOnce 推進一局一輪的內容；回傳是否需要立即再推進一輪。
 * @property failureReporter 接收推進失敗的情況。
 */
@OptIn(ExperimentalAtomicApi::class)
class AutomatedAdvanceManager(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val advanceOnce: suspend (Uuid) -> Boolean,
    private val failureReporter: AutomatedAdvanceFailureReporter,
) {
    /** 每局目前的推進。 */
    private val advances = AtomicReference<Map<Uuid, Advance>>(emptyMap())

    /**
     * 請求推進 [gameId]，不等待推進完成。
     *
     * @param gameId 要推進的遊戲。
     */
    fun request(gameId: Uuid) {
        val registration = Advance()
        if (!claimOrMarkRerun(gameId, registration)) return
        val job = scope.launch(dispatcher) { runRequested(gameId, registration) }
        job.invokeOnCompletion { release(gameId, registration) }
    }

    /**
     * 在呼叫端協程中執行 [block] 推進 [gameId]；這一局已在推進時不執行。
     *
     * @param T [block] 的回傳型別。
     * @param gameId 要推進的遊戲。
     * @param block 推進的內容。
     * @return [block] 的結果；這一局已在推進時為 null。
     */
    suspend fun <T> runExclusive(gameId: Uuid, block: suspend () -> T): T? {
        val registration = Advance()
        if (!claim(gameId, registration)) return null
        try {
            return block()
        } finally {
            if (releaseAndTakeRerun(gameId, registration)) request(gameId)
        }
    }

    /**
     * [gameId] 目前是否正在推進。
     *
     * @param gameId 要查詢的遊戲。
     */
    fun isAdvancing(gameId: Uuid): Boolean = gameId in advances.load()

    /** 開始新的 server session：清空所有登記。 */
    fun startSession() {
        advances.store(emptyMap())
    }

    /** 推進直到沒有新的請求；每一輪開始前清除標記，結束時沒有標記才釋放登記。 */
    private suspend fun runRequested(gameId: Uuid, registration: Advance) {
        do {
            if (!update(gameId, registration) { it.copy(rerun = false) }) return
            val again = try {
                advanceOnce(gameId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                failureReporter.onAdvanceFailed(gameId, error)
                return
            }
        } while (again || !releaseIfIdle(gameId, registration))
    }

    /** 這一局沒有登記時以 [advance] 登記；已有登記時標記它需要再推進一輪。 */
    private fun claimOrMarkRerun(gameId: Uuid, advance: Advance): Boolean {
        while (true) {
            val current = advances.load()
            val existing = current[gameId]
            val next = if (existing == null) current + (gameId to advance) else current + (gameId to existing.copy(rerun = true))
            if (advances.compareAndSet(current, next)) return existing == null
        }
    }

    /** 這一局沒有登記時以 [advance] 登記。 */
    private fun claim(gameId: Uuid, advance: Advance): Boolean {
        while (true) {
            val current = advances.load()
            if (gameId in current) return false
            if (advances.compareAndSet(current, current + (gameId to advance))) return true
        }
    }

    /** 登記仍是 [advance] 且沒有標記時釋放並回傳 true；有標記時保留登記並回傳 false，讓推進再跑一輪。 */
    private fun releaseIfIdle(gameId: Uuid, advance: Advance): Boolean {
        while (true) {
            val current = advances.load()
            val existing = current[gameId]
            if (existing == null || existing.id !== advance.id) return true
            if (existing.rerun) return false
            if (advances.compareAndSet(current, current - gameId)) return true
        }
    }

    /** 釋放 [advance] 的登記，回傳它是否收到過新的請求；登記已不是 [advance] 時不做事並回傳 false。 */
    private fun releaseAndTakeRerun(gameId: Uuid, advance: Advance): Boolean {
        while (true) {
            val current = advances.load()
            val existing = current[gameId]
            if (existing == null || existing.id !== advance.id) return false
            if (advances.compareAndSet(current, current - gameId)) return existing.rerun
        }
    }

    /** 釋放 [advance] 的登記；登記已不是 [advance] 時不做事。 */
    private fun release(gameId: Uuid, advance: Advance) {
        while (true) {
            val current = advances.load()
            val existing = current[gameId]
            if (existing == null || existing.id !== advance.id) return
            if (advances.compareAndSet(current, current - gameId)) return
        }
    }

    /** 只在登記仍是 [advance] 時以 [transform] 更新；回傳登記是否仍是 [advance]。 */
    private fun update(gameId: Uuid, advance: Advance, transform: (Advance) -> Advance): Boolean {
        while (true) {
            val current = advances.load()
            val existing = current[gameId]
            if (existing == null || existing.id !== advance.id) return false
            if (advances.compareAndSet(current, current + (gameId to transform(existing)))) return true
        }
    }

    /** [forCoordinator] 所在的伴生物件。 */
    companion object {
        /**
         * 以 [coordinator] 推進的管理器：每一輪先補完待完成的胡牌或流局流程，有補做就結束這一輪；否則驅動自動操作，最後執行
         * [followUp]，它改變了遊戲時再推進一輪。
         *
         * @param scope 啟動推進協程的作用域。
         * @param dispatcher 推進協程使用的調度器。
         * @param coordinator 推進的流程。
         * @param followUp 每一輪最後補做的平台檢查。
         * @param failureReporter 接收推進失敗的情況。
         */
        fun forCoordinator(
            scope: CoroutineScope,
            dispatcher: CoroutineDispatcher,
            coordinator: GameFlowCoordinator,
            followUp: AutomatedAdvanceFollowUp,
            failureReporter: AutomatedAdvanceFailureReporter,
        ): AutomatedAdvanceManager = AutomatedAdvanceManager(
            scope = scope,
            dispatcher = dispatcher,
            advanceOnce = { gameId ->
                if (coordinator.resumePendingGameTransition(gameId)) {
                    false
                } else {
                    coordinator.driveAutomatedPlayers(gameId)
                    followUp.afterAdvance(gameId)
                }
            },
            failureReporter = failureReporter,
        )
    }

    /**
     * 一局的一次推進登記。
     *
     * @property id 登記的識別；以參考相等比較。
     * @property rerun 推進期間是否收到新的請求。
     */
    private data class Advance(
        val id: Any = Any(),
        val rerun: Boolean = false,
    )
}
