package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlin.uuid.Uuid

/** 接收 AI 決策未能及時取得策略結果的診斷資訊。 */
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
     * 這一局先前已逾時或已取消等待的策略呼叫尚未結束，這次直接使用固定命令；同一個未結束的呼叫只回報一次。
     *
     * @param gameId 對局。
     * @param playerId 決策的 AI 玩家。
     * @param strategyKey 玩家的策略 key；未指定時為 null。
     */
    fun onPreviousStillRunning(gameId: Uuid, playerId: Uuid, strategyKey: String?)

    /**
     * 策略計算丟出一般例外，這次改用固定結果。
     *
     * @param gameId 對局。
     * @param playerId 決策的 AI 玩家。
     * @param strategyKey 玩家的策略 key；未指定時為 null。
     * @param failure 策略丟出的例外。
     */
    fun onStrategyFailed(
        gameId: Uuid,
        playerId: Uuid,
        strategyKey: String?,
        failure: Exception,
    )

    /** [NONE] 所在的伴生物件。 */
    companion object {
        /** 不記錄任何情況的接收者。 */
        val NONE: AiDecisionReporter = object : AiDecisionReporter {
            override fun onTimedOut(gameId: Uuid, playerId: Uuid, strategyKey: String?) = Unit

            override fun onPreviousStillRunning(gameId: Uuid, playerId: Uuid, strategyKey: String?) = Unit

            override fun onStrategyFailed(
                gameId: Uuid,
                playerId: Uuid,
                strategyKey: String?,
                failure: Exception,
            ) = Unit
        }
    }
}
