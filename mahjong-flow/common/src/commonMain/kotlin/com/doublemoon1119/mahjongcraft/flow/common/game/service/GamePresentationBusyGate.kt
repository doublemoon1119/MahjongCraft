package com.doublemoon1119.mahjongcraft.flow.common.game.service

import kotlin.uuid.Uuid

/**
 * 查詢平台是否仍在為 [gameId] 呈現需要等待的內容（例如擲骰）；呈現期間，自動操作流程
 * （`GameFlowCoordinator.driveAutomatedPlayers`）不驅動 AI 與強制自動操作玩家，避免遊戲流程搶在呈現之前推進。
 *
 * 與 [GamePresentationPublisher] 分工：後者是 flow 通知平台的單向出口；這個介面反過來讓 flow 查詢平台的呈現進度，flow
 * 只把結果當成是否等待的訊號，不知道呈現的內容。實作必須是 best-effort：沒有平台實作或該對局不支援呈現時回傳 `false`
 * （視為不忙碌），不能因為查詢失敗就讓自動操作流程卡住。
 */
interface GamePresentationBusyGate {
    /** [gameId] 目前是否仍在呈現需要等待的內容，自動操作流程應該暫停等待。 */
    fun isBusy(gameId: Uuid): Boolean

    /**
     * [gameId] 是否仍在呈現中途胡牌（本局在胡牌後繼續）。
     *
     * 與 [isBusy] 的差異：這段呈現**不**阻擋仍在本局中的玩家繼續摸打，但本局必須等它結束才能換局，否則尚未呈現完的
     * 結算會被下一局的開局呈現取代。因此只有 `GameFlowCoordinator.resumePendingGameTransition` 查詢這一項，
     * `driveAutomatedPlayers` 不查。
     *
     * 預設回傳 `false`。
     */
    fun isPresentingContinuingWin(gameId: Uuid): Boolean = false
}
