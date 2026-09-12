package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationBusyGate
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 依平台呈現忙碌狀態統一暫停或恢復玩家決策計時，並立即同步相同結果。
 *
 * Coordinator、session restore、scheduler 與直接重建權威狀態的 debug 工具應使用本服務，不得自行拼接
 * timer manager 與同步服務。如此 blocking presentation 一開始就會保存剩餘時間並送出停止更新，呈現結束
 * 後才從同一筆剩餘時間恢復。
 *
 * @property busyGate 查詢目前是否有阻擋遊戲流程的呈現。
 * @property timerManager 管理 runtime timer 及其中斷狀態。
 * @property synchronizationService 將本次 availability 結果同步給真人玩家。
 */
@Single
class GameDecisionAvailabilityService(
    @Provided private val busyGate: GamePresentationBusyGate,
    private val timerManager: GameDecisionTimerManager,
    private val synchronizationService: DecisionTimerSynchronizationService,
) {
    /**
     * 依目前呈現狀態調整指定遊戲的決策 availability。
     *
     * @param gameId 欲調整的遊戲識別碼。
     * @param completedPlayerId 剛完成一次決策的玩家；純恢復或週期檢查時為 null。
     * @return 呈現目前是否閒置，可否繼續接受或自動推進遊戲操作。
     */
    suspend fun reconcile(gameId: Uuid, completedPlayerId: Uuid? = null): Boolean {
        val isAvailable = !busyGate.isBusy(gameId)
        val statuses = if (isAvailable) {
            timerManager.reconcile(gameId, completedPlayerId)
        } else {
            timerManager.pause(gameId, completedPlayerId)
            emptyMap()
        }
        synchronizationService.synchronize(gameId, statuses)
        return isAvailable
    }
}
