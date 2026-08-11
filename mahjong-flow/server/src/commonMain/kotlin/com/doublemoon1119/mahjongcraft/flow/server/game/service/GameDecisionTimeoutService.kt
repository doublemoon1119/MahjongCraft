package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import org.koin.core.annotation.Single

/**
 * 將權威決策計時器的完整逾時轉成強制自動操作。
 *
 * @property timerManager 原子取得並標記尚未處理的逾時決策。
 * @property coordinator 驅動已進入強制自動操作的玩家與既有 AI 流程。
 */
@Single
class GameDecisionTimeoutService(
    private val timerManager: GameDecisionTimerManager,
    private val coordinator: GameFlowCoordinator,
) {
    /**
     * 處理目前所有已完整逾時的決策。
     *
     * @return 本次新進入強制自動操作的決策數量。
     */
    suspend fun processExpiredDecisions(): Int {
        val timedOutDecisions = timerManager.claimTimedOutDecisions()
        timedOutDecisions.map(TimedOutPlayerDecision::gameId).distinct().forEach { gameId ->
            coordinator.driveAutomatedPlayers(gameId)
        }
        return timedOutDecisions.size
    }
}
