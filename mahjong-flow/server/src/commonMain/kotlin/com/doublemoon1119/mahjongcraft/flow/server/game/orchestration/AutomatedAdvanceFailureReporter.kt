package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlin.uuid.Uuid

/** 接收自動推進失敗的診斷資訊。 */
fun interface AutomatedAdvanceFailureReporter {
    /**
     * 推進 [gameId] 時發生未預期的錯誤；這次推進已停止，之後的請求照常推進。
     *
     * @param gameId 推進失敗的遊戲。
     * @param error 推進時丟出的錯誤。
     */
    fun onAdvanceFailed(gameId: Uuid, error: Exception)
}
