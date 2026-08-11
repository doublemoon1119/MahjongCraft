package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.DecisionTimeStatus
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionTimer
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * @property time 此次決策在查詢時間點的 A+B 狀態。
 */
data class ActivePlayerDecisionStatus(
    val playerId: Uuid,
    val phase: PlayerDecisionPhase,
    val time: DecisionTimeStatus,
)

/**
 * 管理目前 server session 中所有遊戲的玩家決策計時器。
 *
 * runtime timer 不會寫入 persistence。每次 [reconcile] 會保留仍處於相同決策階段的計時器，並將已完成、
 * 階段改變或失去決策權的計時器所消耗的 B 原子寫回 [GameRepository]。
 *
 * @property gameRepository 權威遊戲狀態倉庫。
 * @property authorityResolver 解析目前具有決策權的玩家與階段。
 * @property timerFactory 依權威剩餘 B 建立新的決策計時器。
 * @property clock 提供計算與結算使用的單調時間。
 */
@Single
class GameDecisionTimerManager(
    private val gameRepository: GameRepository,
    private val authorityResolver: GameDecisionAuthorityResolver,
    private val timerFactory: PlayerDecisionTimerFactory,
    private val clock: MonotonicClock,
) {
    /** 保護 runtime timer 索引及 repository 結算順序的互斥鎖。 */
    private val mutex = Mutex()

    /** 以遊戲及玩家識別碼索引目前進行中的決策計時器。 */
    private val activeTimers = mutableMapOf<Uuid, Map<Uuid, ActiveDecisionTimer>>()

    /**
     * 依目前權威遊戲狀態調整決策計時器。
     *
     * [completedPlayerId] 的既有計時器必定先結算；若狀態轉移後該玩家再次取得決策權，會以結算後的 B
     * 建立新計時器並重新取得 A。其他仍處於相同 decision phase 的玩家不會重設。
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
            val updatedGame = currentGame.copy(
                remainingReserveMillisByPlayerId = remainingReserveMillisByPlayerId,
            )
            updatedGame to Reconciliation(updatedGame, targets)
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
                    timer = timerFactory.create(reconciliation.game, playerId),
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
     * @property game 已結算失效 timer 所消耗 B 的權威遊戲狀態。
     * @property targets 目前仍需要計時的玩家與階段。
     */
    private data class Reconciliation(
        val game: Game,
        val targets: Map<Uuid, PlayerDecisionPhase>,
    )

    /**
     * runtime 中單一玩家目前的決策階段與計時器。
     *
     * @property phase 玩家目前所處的決策階段。
     * @property timer 該次決策的 A+B 計時器。
     */
    private data class ActiveDecisionTimer(
        val phase: PlayerDecisionPhase,
        val timer: PlayerDecisionTimer,
    )
}
