package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 將權威決策計時器的完整逾時轉成強制自動操作。
 *
 * @property timerManager 原子取得並標記尚未處理的逾時決策。
 * @property advanceManager 為受影響的對局排程推進，由推進代打已進入強制自動操作的玩家並接續既有 AI 流程。
 */
@Single
class GameDecisionTimeoutService(
    private val timerManager: GameDecisionTimerManager,
    private val advanceManager: AutomatedAdvanceManager,
) {
    /**
     * 處理目前所有已完整逾時的決策：認領逾時並為受影響的對局請求推進，不等待推進完成。
     *
     * @return 本次已請求推進的對局 Uuid 集合；呼叫端可據此略過同一輪對這些對局的重複請求。
     */
    suspend fun processExpiredDecisions(): Set<Uuid> {
        val timedOutDecisions = timerManager.claimTimedOutDecisions()
        val affectedGameIds = timedOutDecisions.map(TimedOutPlayerDecision::gameId).toSet()
        affectedGameIds.forEach(advanceManager::request)
        return affectedGameIds
    }
}
