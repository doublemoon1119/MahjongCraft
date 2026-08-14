package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

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
     * @return 本次因逾時而被推進過（`driveAutomatedPlayers`）的對局 Uuid 集合；呼叫端可依此對每個
     * 受影響的對局補做只有平台層才知道的後續檢查（例如真人玩家的自動摸牌）。
     */
    suspend fun processExpiredDecisions(): Set<Uuid> {
        val timedOutDecisions = timerManager.claimTimedOutDecisions()
        val affectedGameIds = timedOutDecisions.map(TimedOutPlayerDecision::gameId).distinct()
        affectedGameIds.forEach { gameId -> coordinator.driveAutomatedPlayers(gameId) }
        return affectedGameIds.toSet()
    }
}
