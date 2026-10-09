package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** 接收 AI 決策沒有等到策略結果的情況，供平台記錄。 */
interface AiDecisionReporter {
    /**
     * 等待策略的決策超過上限，這次改用固定命令。
     *
     * @param gameId 對局。
     * @param playerId 決策的 AI 玩家。
     * @param strategyKey 玩家的策略 key；未指定時為 null。
     */
    fun onTimedOut(gameId: Uuid, playerId: Uuid, strategyKey: String?)

    /**
     * 這一局先前逾時的策略呼叫尚未結束，這次直接使用固定命令；同一個未結束的呼叫只回報一次。
     *
     * @param gameId 對局。
     * @param playerId 決策的 AI 玩家。
     * @param strategyKey 玩家的策略 key；未指定時為 null。
     */
    fun onPreviousStillRunning(gameId: Uuid, playerId: Uuid, strategyKey: String?)

    /** [NONE] 所在的伴生物件。 */
    companion object {
        /** 不記錄任何情況的接收者。 */
        val NONE: AiDecisionReporter = object : AiDecisionReporter {
            override fun onTimedOut(gameId: Uuid, playerId: Uuid, strategyKey: String?) = Unit

            override fun onPreviousStillRunning(gameId: Uuid, playerId: Uuid, strategyKey: String?) = Unit
        }
    }
}

/**
 * 在 AI 專用的調度器上呼叫策略，並限制等待時間與同時存在的策略工作。
 *
 * - 名額：同時存在、尚未真正結束的策略工作最多 [capacity] 個。取得名額後才建立策略工作，建立時立即登記完成回呼，由回呼釋放
 *   名額（完成回呼只會觸發一次）；工作在開始執行前就被取消時本文不會執行，回呼仍會釋放名額。不檢查取消的工作要等本文真正
 *   返回才完成，名額一直占用到那時。
 * - 等待上限：[timeout] 是等待這次結果的上限（含等待名額），不是策略會在這段時間內停止的保證。逾時後要求取消策略工作並改用
 *   固定命令；已在執行、不檢查取消的工作只丟棄結果。
 * - 同一局：同一局同一時間只會有一次策略呼叫在執行。已有呼叫正在正常計算時，這次不決策；先前的呼叫逾時（或等待它的協程被
 *   取消）但尚未結束時，這次直接使用固定命令，直到那個呼叫結束。
 *
 * 名額與每局記錄都屬於這個實例，不隨 server session 重建：舊 session 留下的不合作工作仍計入名額，且同一局在它結束前都使用
 * 固定命令；等待它的協程隨舊 session 取消，因此結果不會提交。每局記錄由完成回呼依工作識別碼清除，可能在任何執行緒上更新，
 * 以原子操作保護。
 *
 * @param dispatcher 執行策略的調度器。
 * @property capacity 同時存在、尚未結束的策略工作上限。
 * @property timeout 等待一次決策結果的上限。
 * @property reporter 接收逾時與舊呼叫仍未結束的情況。
 */
@OptIn(ExperimentalAtomicApi::class)
class AiDecisionExecutor(
    dispatcher: CoroutineDispatcher,
    private val capacity: Int,
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val reporter: AiDecisionReporter = AiDecisionReporter.NONE,
) {
    init {
        require(capacity > 0) { "AI decision capacity must be positive" }
        require(timeout.isPositive()) { "AI decision timeout must be positive" }
    }

    /** 尚未真正結束的策略工作名額。 */
    private val permits = Semaphore(capacity)

    /** 執行策略工作的作用域；不隨任何呼叫端取消，工作失敗也不影響其他工作。 */
    private val workerScope = CoroutineScope(SupervisorJob() + dispatcher)

    /** 每局目前的策略呼叫；呼叫結束時由完成回呼移除。 */
    private val calls = AtomicReference<Map<Uuid, StrategyCall>>(emptyMap())

    /**
     * 為 [gameId] 的 [playerId] 計算一次決策。
     *
     * @param T 決策結果的型別。
     * @param gameId 對局。
     * @param playerId 決策的 AI 玩家。
     * @param strategyKey 玩家的策略 key，只用於回報。
     * @param decide 呼叫策略的計算，在執行策略的調度器上執行。
     * @param fallback 沒有等到策略結果時使用的固定結果，在呼叫端的執行緒上計算。
     * @return 策略或固定結果；這一局已有策略呼叫正在正常計算時為 null，這次不決策。
     */
    suspend fun <T : Any> decide(
        gameId: Uuid,
        playerId: Uuid,
        strategyKey: String?,
        decide: suspend () -> T,
        fallback: () -> T,
    ): T? {
        val call = StrategyCall()
        when (val claim = claim(gameId, call)) {
            Claim.Granted -> Unit
            Claim.Busy -> return null
            is Claim.Abandoned -> {
                if (claim.firstReport) reporter.onPreviousStillRunning(gameId, playerId, strategyKey)
                return fallback()
            }
        }
        var started: Deferred<T>? = null
        val value = try {
            withTimeoutOrNull(timeout) {
                permits.acquire()
                // 取得名額到建立工作之間沒有掛起點，名額不會在登記完成回呼前遺失。
                val work = workerScope.async { decide() }
                work.invokeOnCompletion {
                    permits.release()
                    finish(gameId, call)
                }
                started = work
                work.await()
            }
        } catch (cancellation: CancellationException) {
            abandon(gameId, call, started)
            throw cancellation
        }
        if (value != null) return value
        abandon(gameId, call, started)
        reporter.onTimedOut(gameId, playerId, strategyKey)
        return fallback()
    }

    /** 嘗試讓 [call] 成為 [gameId] 目前的策略呼叫。 */
    private fun claim(gameId: Uuid, call: StrategyCall): Claim {
        while (true) {
            val current = calls.load()
            val existing = current[gameId]
            val (next, claim) = when {
                existing == null -> current + (gameId to call) to Claim.Granted
                !existing.abandoned -> return Claim.Busy
                existing.reported -> return Claim.Abandoned(firstReport = false)
                else -> current + (gameId to existing.copy(reported = true)) to Claim.Abandoned(firstReport = true)
            }
            if (calls.compareAndSet(current, next)) return claim
        }
    }

    /** 不再等待 [call]：沒有建立工作時直接清除記錄，否則要求取消工作，並在它結束前把這一局標成仍有未結束的呼叫。 */
    private fun abandon(gameId: Uuid, call: StrategyCall, work: Deferred<*>?) {
        if (work == null) {
            finish(gameId, call)
            return
        }
        work.cancel()
        update(gameId, call) { it.copy(abandoned = true) }
    }

    /** 清除 [call] 的記錄；記錄已不是 [call] 時不做事。 */
    private fun finish(gameId: Uuid, call: StrategyCall) = update(gameId, call) { null }

    /** 只在 [gameId] 目前的記錄仍是 [call] 時以 [transform] 更新（回傳 null 代表移除）。 */
    private fun update(gameId: Uuid, call: StrategyCall, transform: (StrategyCall) -> StrategyCall?) {
        while (true) {
            val current = calls.load()
            val existing = current[gameId]
            if (existing == null || existing.id !== call.id) return
            val replaced = transform(existing)
            val next = if (replaced == null) current - gameId else current + (gameId to replaced)
            if (calls.compareAndSet(current, next)) return
        }
    }

    /**
     * 一局的一次策略呼叫。
     *
     * @property id 呼叫的識別；以參考相等比較。
     * @property abandoned 等待結果的呼叫端已放棄等待，但工作尚未結束。
     * @property reported 已回報過這個未結束的呼叫。
     */
    private data class StrategyCall(
        val id: Any = Any(),
        val abandoned: Boolean = false,
        val reported: Boolean = false,
    )

    /** 嘗試成為一局目前策略呼叫的結果。 */
    private sealed interface Claim {
        /** 可以呼叫策略。 */
        data object Granted : Claim

        /** 已有呼叫正在正常計算。 */
        data object Busy : Claim

        /**
         * 先前的呼叫已逾時但尚未結束。
         *
         * @property firstReport 這是第一次遇到這個未結束的呼叫，需要回報。
         */
        data class Abandoned(val firstReport: Boolean) : Claim
    }

    /** [DEFAULT_TIMEOUT] 與 [direct] 所在的伴生物件。 */
    companion object {
        /** 預設的等待上限。 */
        val DEFAULT_TIMEOUT: Duration = 5.seconds

        /**
         * 在呼叫端的執行緒上直接執行策略的執行器：策略沒有掛起時，計算在呼叫內同步完成。供不需要背景計算的執行環境
         * （例如測試或依序推進的離線對局）使用。
         *
         * @param reporter 接收逾時與舊呼叫仍未結束的情況。
         */
        fun direct(reporter: AiDecisionReporter = AiDecisionReporter.NONE): AiDecisionExecutor = AiDecisionExecutor(Dispatchers.Unconfined, capacity = Int.MAX_VALUE, reporter = reporter)
    }
}
