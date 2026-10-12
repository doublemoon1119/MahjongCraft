package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

/**
 * 每一局同一時間只有一個自動推進：所有驅動自動操作的入口都向這裡請求，不直接驅動。
 *
 * - [request]：這一局已在推進時，只標記「需要再推進一輪」並返回；否則登記這一局並在 [scope] 上以 [dispatcher] 啟動推進協程，
 *   不等待該工作完成。是否會在呼叫返回前開始或完成，取決於調度器；立即執行的調度器可能在首次掛起前同步執行推進本文。推進協程
 *   每一輪結束時檢查標記，有標記就再推進一輪，因此不會漏掉推進期間的請求。
 * - [runExclusive]：在呼叫端協程中執行一段推進（例如逐步推進的離線對局）；這一局已在推進時略過。執行期間收到的 [request]
 *   在它結束後才推進。
 *
 * 登記在啟動協程之前完成，避免協程尚未開始時重複排入。登記的釋放不依賴推進本文：協程建立時立即登記完成回呼，由回呼依登記的
 * 識別碼清除，所以協程在開始前就被取消時也會釋放；清除以識別碼比對，重複清除不會影響之後的登記。一局推進失敗時交給
 * [failureReporter] 並停止這次推進，不影響其他局；取消會照常往外傳。[startSession] 只清空登記、不取消舊 session 的工作；舊工作
 * 仍可能繼續推進，並與新 session 對同一局的推進重疊。若不允許重疊，呼叫端須先取消或等待舊工作結束，再開始新 session。舊
 * session 的協程結束時因識別碼不同，不會清掉新 session 的登記。完成回呼可能在任何執行緒上觸發，登記以原子操作保護。
 *
 * @property scope 啟動推進協程的作用域，由呼叫端配合目前執行階段管理生命週期。
 * @property dispatcher 執行權威流程的調度器。
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

    /** 開始新的執行階段並清空登記；不會取消先前的工作，呼叫端須另外管理其生命週期。 */
    fun startSession() {
        advances.store(emptyMap())
    }

    /**
     * 推進直到沒有新的請求；每輪開始前清除標記，結束時沒有標記才釋放登記。
     *
     * @param gameId 要推進的遊戲。
     * @param registration 本次推進持有的登記。
     */
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

    /**
     * 嘗試登記推進；已有登記時標記需要再推進一輪。
     *
     * @param gameId 要推進的遊戲。
     * @param advance 欲建立的登記。
     * @return 是否成功建立新登記。
     */
    private fun claimOrMarkRerun(gameId: Uuid, advance: Advance): Boolean {
        while (true) {
            val current = advances.load()
            val existing = current[gameId]
            val next = if (existing == null) current + (gameId to advance) else current + (gameId to existing.copy(rerun = true))
            if (advances.compareAndSet(current, next)) return existing == null
        }
    }

    /**
     * 只在這一局沒有登記時建立登記。
     *
     * @param gameId 要推進的遊戲。
     * @param advance 欲建立的登記。
     * @return 是否成功建立登記。
     */
    private fun claim(gameId: Uuid, advance: Advance): Boolean {
        while (true) {
            val current = advances.load()
            if (gameId in current) return false
            if (advances.compareAndSet(current, current + (gameId to advance))) return true
        }
    }

    /**
     * 在沒有補跑請求時釋放自己的登記。
     *
     * @param gameId 要檢查的遊戲。
     * @param advance 預期持有的登記。
     * @return 登記已釋放或已不屬於本次推進時為 true；仍有補跑請求時為 false。
     */
    private fun releaseIfIdle(gameId: Uuid, advance: Advance): Boolean {
        while (true) {
            val current = advances.load()
            val existing = current[gameId]
            if (existing == null || existing.id !== advance.id) return true
            if (existing.rerun) return false
            if (advances.compareAndSet(current, current - gameId)) return true
        }
    }

    /**
     * 釋放自己的登記並取出補跑狀態。
     *
     * @param gameId 要釋放的遊戲。
     * @param advance 預期持有的登記。
     * @return 是否有補跑請求；登記已不屬於本次推進時為 false。
     */
    private fun releaseAndTakeRerun(gameId: Uuid, advance: Advance): Boolean {
        while (true) {
            val current = advances.load()
            val existing = current[gameId]
            if (existing == null || existing.id !== advance.id) return false
            if (advances.compareAndSet(current, current - gameId)) return existing.rerun
        }
    }

    /**
     * 釋放自己的登記；目前登記已屬於其他推進時不做事。
     *
     * @param gameId 要釋放的遊戲。
     * @param advance 預期持有的登記。
     */
    private fun release(gameId: Uuid, advance: Advance) {
        while (true) {
            val current = advances.load()
            val existing = current[gameId]
            if (existing == null || existing.id !== advance.id) return
            if (advances.compareAndSet(current, current - gameId)) return
        }
    }

    /**
     * 只更新仍屬於本次推進的登記。
     *
     * @param gameId 要更新的遊戲。
     * @param advance 預期持有的登記。
     * @param transform 計算新登記；競爭更新時可能重算。
     * @return 是否成功更新自己的登記。
     */
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
         * @param followUp 自動操作完成後的流程檢查。
         * @param failureReporter 接收推進失敗的情況。
         * @return 統一處理待完成流程、自動操作與後續檢查的管理器。
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
