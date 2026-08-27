package com.doublemoon1119.mahjongcraft.flow.common.game.service

import kotlin.uuid.Uuid

/**
 * 查詢平台呈現層是否仍在為 [gameId] 播放呈現動畫（例如擲骰）——播放期間，自動操作鏈路
 * （`com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator.driveAutomatedPlayers`）
 * 不該繼續驅動 AI／強制自動操作玩家，避免遊戲流程搶在畫面之前推進。
 *
 * 與 [GamePresentationPublisher] 分工明確：後者是「`mahjong-flow` 通知平台層發生了什麼事」的
 * 單向出口；這個介面反過來是「平台層自己知道的呈現進度，`mahjong-flow` 需要查詢」，`mahjong-flow`
 * 本身完全不知道、也不需要知道「呈現動畫」這個概念本身是什麼，只把查詢結果當成一個不透明的
 * 布林號誌使用。實作方必須是 best-effort：沒有平台實作、或該桌不是對應平台的桌子時，回傳 `false`
 * （視為不忙碌），不能因為查詢本身失敗就讓自動操作鏈路整個卡住。
 */
interface GamePresentationBusyGate {
    /** [gameId] 目前是否仍在播放呈現動畫，自動操作鏈路應該暫停等待。 */
    fun isBusy(gameId: Uuid): Boolean

    /**
     * [gameId] 是否仍在播放中途胡牌演出（[PresentationTimeline.CONTINUING_WIN]）。
     *
     * 與 [isBusy] 的關鍵差異：這種演出刻意**不**阻擋其他仍在本局中的玩家繼續摸打——否則已完成玩家
     * 的結算面板會讓整桌乾等。但本局必須等它播完才能換局，否則排隊中的結算面板會被新一局的發牌
     * 動畫直接蓋掉。因此只有 `GameFlowCoordinator.resumePendingGameTransition` 查詢這一項，
     * `driveAutomatedPlayers` 不查。
     *
     * 預設回傳 `false`，讓不支援這個概念的平台實作零改動。
     */
    fun isPresentingContinuingWin(gameId: Uuid): Boolean = false
}
