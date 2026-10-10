package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlin.uuid.Uuid

/** 每次自動推進完成後的流程檢查，例如替真人玩家自動摸牌。 */
fun interface AutomatedAdvanceFollowUp {
    /**
     * 推進 [gameId] 後執行流程後續檢查。
     *
     * @param gameId 剛推進的遊戲。
     * @return 是否改變了遊戲；改變時 [AutomatedAdvanceManager] 會再推進一輪。
     */
    suspend fun afterAdvance(gameId: Uuid): Boolean
}
