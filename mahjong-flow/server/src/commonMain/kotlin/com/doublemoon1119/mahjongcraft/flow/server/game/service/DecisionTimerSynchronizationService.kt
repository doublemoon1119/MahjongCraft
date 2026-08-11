package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdate
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 將 server timer manager 的權威狀態同步給目前具有決策權的真人玩家。
 *
 * 服務會記住上次已同步的玩家，讓失去決策權、階段結束或完整逾時時能送出停止更新。AI 不需要畫面
 * 計時，因此不會交給 [publisher]。
 *
 * @property timerManager 提供目前 runtime 中的權威計時。
 * @property gameRepository 判斷決策者是否為真人玩家。
 * @property publisher 將平台無關的更新送至實際連線。
 */
@Single
class DecisionTimerSynchronizationService(
    private val timerManager: GameDecisionTimerManager,
    private val gameRepository: GameRepository,
    @Provided private val publisher: DecisionTimerUpdatePublisher,
) {
    /** 保護上次同步玩家索引，避免立即同步與週期同步交錯。 */
    private val mutex = Mutex()

    /** 以遊戲索引上次收到有效計時更新的真人玩家。 */
    private val synchronizedPlayerIdsByGame = mutableMapOf<Uuid, Set<Uuid>>()

    /** 讀取 timer manager 並同步指定遊戲。 */
    suspend fun synchronize(gameId: Uuid) {
        synchronize(gameId, timerManager.getStatuses(gameId))
    }

    /**
     * 使用已取得的 [statuses] 同步指定遊戲，避免命令完成後再次讀取不同時間點。
     *
     * @param gameId 欲同步的遊戲。
     * @param statuses 目前所有決策者的權威計時。
     */
    suspend fun synchronize(gameId: Uuid, statuses: Map<Uuid, ActivePlayerDecisionStatus>) = mutex.withLock {
        val game = gameRepository.getGame(gameId)
        val humanPlayerIds = game?.tableState?.players.orEmpty().filterNot { it.isAi }.mapTo(mutableSetOf()) { it.id }
        val activeHumanStatuses = statuses.filterKeys { it in humanPlayerIds }
        val previousPlayerIds = synchronizedPlayerIdsByGame[gameId].orEmpty()

        activeHumanStatuses.forEach { (playerId, status) ->
            publisher.publish(
                playerId,
                DecisionTimerUpdate.Active(
                    gameId = gameId,
                    phase = status.phase,
                    baseRemainingMillis = status.time.baseRemainingMillis,
                    reserveRemainingMillis = status.time.reserveRemainingMillis,
                ),
            )
        }
        (previousPlayerIds - activeHumanStatuses.keys).forEach { playerId ->
            publisher.publish(playerId, DecisionTimerUpdate.Stopped(gameId))
        }

        if (activeHumanStatuses.isEmpty()) {
            synchronizedPlayerIdsByGame.remove(gameId)
        } else {
            synchronizedPlayerIdsByGame[gameId] = activeHumanStatuses.keys
        }
    }

    /** 週期同步目前所有有效計時，並停止已不存在的舊遊戲計時。 */
    suspend fun synchronizeAll() {
        val allStatuses = timerManager.getAllStatuses()
        val previouslySynchronizedGameIds = mutex.withLock { synchronizedPlayerIdsByGame.keys.toSet() }
        (allStatuses.keys + previouslySynchronizedGameIds).forEach { gameId ->
            synchronize(gameId, allStatuses[gameId].orEmpty())
        }
    }

    /** 清除 server session 結束後不再有效的同步索引。 */
    suspend fun clear() = mutex.withLock {
        synchronizedPlayerIdsByGame.clear()
    }
}
