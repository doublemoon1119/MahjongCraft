package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.DecisionTimeStatus
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionTimer
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 目前有效的玩家決策及其權威時間狀態。
 *
 * 此 read model 由 [GameDecisionTimerManager] 提供給 scheduler 與外層同步服務，不寫入 persistence，
 * 也不直接作為網路 DTO。
 *
 * @property playerId 目前具有決策權的玩家。
 * @property phase 玩家目前所處的決策階段。
 * @property time 此次決策在查詢時間點的基本思考時間與保留思考時間狀態。
 */
data class ActivePlayerDecisionStatus(
    val playerId: Uuid,
    val phase: PlayerDecisionPhase,
    val time: DecisionTimeStatus,
)

/**
 * scheduler 已取得處理權的一次完整逾時決策。
 *
 * @property gameId 發生逾時的遊戲。
 * @property playerId 耗盡全部思考時間的玩家。
 * @property phase 發生逾時時的決策階段。
 */
data class TimedOutPlayerDecision(
    val gameId: Uuid,
    val playerId: Uuid,
    val phase: PlayerDecisionPhase,
)

/**
 * 管理目前 server session 中所有遊戲的玩家決策計時器。
 *
 * runtime timer 不會寫入 persistence。每次 [reconcile] 會保留仍處於相同決策階段的計時器，並將已完成、
 * 階段改變或失去決策權的計時器所消耗的保留思考時間原子寫回 [GameRepository]。
 *
 * @property gameRepository 權威遊戲狀態倉庫。
 * @property authorityResolver 解析目前具有決策權的玩家與階段。
 * @property timerFactory 依權威剩餘保留思考時間建立新的決策計時器。
 * @property clock 提供計算與結算使用的單調時間；實作由平台層提供，理由見 [MonotonicClock] KDoc。
 */
@Single
class GameDecisionTimerManager(
    private val gameRepository: GameRepository,
    private val authorityResolver: GameDecisionAuthorityResolver,
    private val timerFactory: PlayerDecisionTimerFactory,
    @Provided private val clock: MonotonicClock,
) {
    /** 保護 runtime timer 索引及 repository 結算順序的互斥鎖。 */
    private val mutex = Mutex()

    /** 以遊戲及玩家識別碼索引目前進行中的決策計時器。 */
    private val activeTimers = mutableMapOf<Uuid, Map<Uuid, ActiveDecisionTimer>>()

    /**
     * 依目前權威遊戲狀態調整決策計時器。
     *
     * [completedPlayerId] 的既有計時器必定先結算；若狀態轉移後該玩家再次取得決策權，會以結算後的
     * 保留思考時間
     * 建立新計時器並重新取得基本思考時間。其他仍處於相同 decision phase 的玩家不會重設。
     *
     * @param gameId 欲調整計時器的遊戲識別碼。
     * @param completedPlayerId 剛完成一次決策的玩家；純狀態恢復或首次建立時為 null。
     * @return 調整完成後所有目前決策者的權威時間狀態。
     */
    suspend fun reconcile(gameId: Uuid, completedPlayerId: Uuid? = null): Map<Uuid, ActivePlayerDecisionStatus> = mutex.withLock {
        val previousTimers = activeTimers[gameId].orEmpty()
        val settledAtMillis = clock.nowMillis()
        val reconciliation = gameRepository.updateGame(gameId) { currentGame ->
            if (currentGame == null) return@updateGame null to null

            val targets = authorityResolver.resolve(currentGame)
            val timersToSettle = previousTimers.filter { (playerId, activeTimer) ->
                playerId == completedPlayerId || targets[playerId] != activeTimer.phase
            }
            val remainingReserveMillisByPlayerId = currentGame.remainingReserveMillisByPlayerId.toMutableMap()
            timersToSettle.forEach { (playerId, activeTimer) ->
                remainingReserveMillisByPlayerId[playerId] = activeTimer.timer
                    .statusAt(settledAtMillis)
                    .reserveRemainingMillis
            }
            // 保留上一個 session 中斷的基本思考時間供下方建立計時器使用，但權威狀態本身直接消耗掉：
            // 已結算的決策不再接續，而重建過的計時器也只能接續一次，否則下一次 reconcile 會重複套用。
            val keptPlayerIds = previousTimers.filterKeys { playerId ->
                playerId != completedPlayerId && targets[playerId] == previousTimers.getValue(playerId).phase
            }.keys
            val resumeBaseMillisByPlayerId = currentGame.interruptedBaseMillisByPlayerId
                .filterKeys { it in targets.keys && it !in keptPlayerIds }
            val updatedGame = currentGame.copy(
                remainingReserveMillisByPlayerId = remainingReserveMillisByPlayerId,
                interruptedBaseMillisByPlayerId = currentGame.interruptedBaseMillisByPlayerId -
                    resumeBaseMillisByPlayerId.keys - timersToSettle.keys,
            )
            updatedGame to Reconciliation(updatedGame, targets, resumeBaseMillisByPlayerId)
        }

        if (reconciliation == null) {
            activeTimers.remove(gameId)
            return@withLock emptyMap()
        }

        val nextTimers = reconciliation.targets.mapValues { (playerId, phase) ->
            previousTimers[playerId]
                ?.takeIf { playerId != completedPlayerId && it.phase == phase }
                ?: ActiveDecisionTimer(
                    phase = phase,
                    timer = timerFactory.create(
                        game = reconciliation.game,
                        playerId = playerId,
                        resumedBaseMillis = reconciliation.resumeBaseMillisByPlayerId[playerId],
                    ),
                )
        }
        if (nextTimers.isEmpty()) {
            activeTimers.remove(gameId)
        } else {
            activeTimers[gameId] = nextTimers
        }
        statusesAt(nextTimers, clock.nowMillis())
    }

    /**
     * 取得指定遊戲目前所有決策者的權威時間狀態。
     *
     * @param gameId 欲查詢的遊戲識別碼。
     * @return 以玩家識別碼索引的時間狀態；尚未建立計時器時為空 map。
     */
    suspend fun getStatuses(gameId: Uuid): Map<Uuid, ActivePlayerDecisionStatus> = mutex.withLock {
        statusesAt(activeTimers[gameId].orEmpty(), clock.nowMillis())
    }

    /** 取得目前所有遊戲及決策者的權威時間狀態。 */
    suspend fun getAllStatuses(): Map<Uuid, Map<Uuid, ActivePlayerDecisionStatus>> = mutex.withLock {
        val nowMillis = clock.nowMillis()
        activeTimers.mapValues { (_, timers) -> statusesAt(timers, nowMillis) }
    }

    /**
     * 取得並標記目前所有完整逾時的玩家，確保同一次逾時只交給 scheduler 處理一次。
     *
     * 玩家會在同一個 repository 交易中耗盡保留思考時間並進入強制自動操作；對應 runtime timer
     * 隨即移除，後續決策由自動操作 driver 直接處理，不再建立新的思考計時器。
     */
    suspend fun claimTimedOutDecisions(): List<TimedOutPlayerDecision> = mutex.withLock {
        val nowMillis = clock.nowMillis()
        val claimed = mutableListOf<TimedOutPlayerDecision>()
        activeTimers.toMap().forEach { (gameId, timers) ->
            val timedOut = timers.filterValues { it.timer.statusAt(nowMillis).isTimedOut }
            if (timedOut.isEmpty()) return@forEach

            gameRepository.updateGame(gameId) { currentGame ->
                if (currentGame == null) return@updateGame null to Unit
                val timedOutPlayerIds = timedOut.keys.intersect(currentGame.remainingReserveMillisByPlayerId.keys)
                val remainingReserveMillisByPlayerId = currentGame.remainingReserveMillisByPlayerId.toMutableMap()
                timedOutPlayerIds.forEach { remainingReserveMillisByPlayerId[it] = 0L }
                currentGame.copy(
                    remainingReserveMillisByPlayerId = remainingReserveMillisByPlayerId,
                    forcedAutoPlayPlayerIds = currentGame.forcedAutoPlayPlayerIds + timedOutPlayerIds,
                ) to Unit
            }

            claimed += timedOut.map { (playerId, activeTimer) ->
                TimedOutPlayerDecision(gameId, playerId, activeTimer.phase)
            }
            val remainingTimers = timers - timedOut.keys
            if (remainingTimers.isEmpty()) activeTimers.remove(gameId) else activeTimers[gameId] = remainingTimers
        }
        claimed
    }

    /**
     * 結算目前 session 的所有決策計時器並清除 runtime 索引。
     *
     * 平台停止 server session 時必須先停止新的遊戲命令，再呼叫本方法，最後才解除 persistence dirty
     * listener；如此最後使用的保留思考時間才會進入平台已快取的權威存檔快照。每場遊戲各自以一次 repository
     * 交易寫回所有玩家的剩餘保留思考時間。「先停止新的遊戲命令」這件事本身，
     * 交給呼叫端用 [AppCoroutineScope.shutdown] 完成——
     * 停止接受新工作、等現有工作自然跑完，而不是直接 `cancel()` 把還在等鎖、卡在中途的協程攔腰砍斷。
     */
    suspend fun settleAll() = mutex.withLock {
        val settledAtMillis = clock.nowMillis()
        activeTimers.forEach { (gameId, timers) ->
            gameRepository.updateGame(gameId) { currentGame ->
                if (currentGame == null) return@updateGame null to Unit
                val remainingReserveMillisByPlayerId = currentGame.remainingReserveMillisByPlayerId.toMutableMap()
                val interruptedBaseMillisByPlayerId = currentGame.interruptedBaseMillisByPlayerId.toMutableMap()
                val exhaustedPlayerIds = mutableSetOf<Uuid>()
                timers.forEach { (playerId, activeTimer) ->
                    val status = activeTimer.timer.statusAt(settledAtMillis)
                    remainingReserveMillisByPlayerId[playerId] = status.reserveRemainingMillis
                    if (status.isTimedOut) {
                        // 已耗盡全部思考時間卻還沒被 scheduler 取走的決策，直接比照 claimTimedOutDecisions
                        // 進入強制自動操作；不寫入中斷的基本思考時間，下一個 session 也不會再為它建計時器。
                        exhaustedPlayerIds += playerId
                        interruptedBaseMillisByPlayerId -= playerId
                    } else {
                        interruptedBaseMillisByPlayerId[playerId] = status.baseRemainingMillis
                    }
                }
                currentGame.copy(
                    remainingReserveMillisByPlayerId = remainingReserveMillisByPlayerId,
                    forcedAutoPlayPlayerIds = currentGame.forcedAutoPlayPlayerIds + exhaustedPlayerIds,
                    interruptedBaseMillisByPlayerId = interruptedBaseMillisByPlayerId,
                ) to Unit
            }
        }
        activeTimers.clear()
    }

    /** 清除目前 session 的所有 runtime timer，不修改已保存的權威遊戲狀態。 */
    suspend fun clearAll() = mutex.withLock {
        activeTimers.clear()
    }

    /** 將指定時間點的 runtime timers 轉成對外 read model。 */
    private fun statusesAt(
        timers: Map<Uuid, ActiveDecisionTimer>,
        nowMillis: Long,
    ): Map<Uuid, ActivePlayerDecisionStatus> = timers.mapValues { (playerId, activeTimer) ->
        val status = activeTimer.timer.statusAt(nowMillis)
        ActivePlayerDecisionStatus(
            playerId = playerId,
            phase = activeTimer.phase,
            time = status,
        )
    }

    /**
     * 一次 repository reconciliation 取得的更新後遊戲與決策目標。
     *
     * @property game 已結算失效 timer 所消耗保留思考時間的權威遊戲狀態。
     * @property targets 目前仍需要計時的玩家與階段。
     * @property resumeBaseMillisByPlayerId 本次重建計時器時該接續的剩餘基本思考時間；已從 [game] 消耗。
     */
    private data class Reconciliation(
        val game: Game,
        val targets: Map<Uuid, PlayerDecisionPhase>,
        val resumeBaseMillisByPlayerId: Map<Uuid, Long>,
    )

    /**
     * runtime 中單一玩家目前的決策階段與計時器。
     *
     * @property phase 玩家目前所處的決策階段。
     * @property timer 該次決策的基本思考時間與保留思考時間計時器。
     */
    private data class ActiveDecisionTimer(
        val phase: PlayerDecisionPhase,
        val timer: PlayerDecisionTimer,
    )
}
