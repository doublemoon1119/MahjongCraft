package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

/**
 * 供開發工具操作牌譜畫面的入口：每個操作都與玩家點擊對應的按鈕相同，畫面切換照正式的瀏覽流程進行。
 *
 * 只在用戶端主執行緒呼叫。沒有開啟牌譜瀏覽，或目前頁面不能這樣切換時，操作回傳 false。
 */
interface HistoryScreenAutomation {
    /**
     * 目前牌譜瀏覽的狀態。
     *
     * @return 狀態；沒有開啟牌譜瀏覽時為 null。
     */
    fun automationSnapshot(): HistoryScreenAutomationSnapshot?

    /**
     * 讓列表顯示所有人的對局，與在篩選條件選擇「全部」相同；需要有查閱全部對局的權限。
     *
     * @return 是否已接受並重新查詢。
     */
    fun automationShowAllMatches(): Boolean

    /**
     * 開啟一場對局的摘要；對局必須在列表目前這一頁。
     *
     * @param matchId 對局識別碼。
     * @return 是否已切換。
     */
    fun automationOpenSummary(matchId: String): Boolean

    /**
     * 從摘要開啟規則設定。
     *
     * @return 是否已切換。
     */
    fun automationOpenRuleSettings(): Boolean

    /**
     * 從規則設定或單局事件回到摘要。
     *
     * @return 是否已切換。
     */
    fun automationBackToSummary(): Boolean

    /**
     * 從摘要開啟一局的事件。
     *
     * @param roundNumber 摘要中的局序號，從 1 起算。
     * @return 是否已切換。
     */
    fun automationOpenRound(roundNumber: Int): Boolean

    /**
     * 從單局事件開啟某一刻的牌面。
     *
     * @param transactionIndex 這一筆交易完成後的牌面；null 是這一局的初始牌面。
     * @return 是否已切換。
     */
    fun automationOpenRoundState(transactionIndex: Int?): Boolean

    /**
     * 從牌面或回放回到單局事件。
     *
     * @return 是否已切換。
     */
    fun automationBackToRound(): Boolean

    /**
     * 從單局事件開始回放，停在指定步數。
     *
     * @param step 步數：0 是初始牌面，k 是第 k - 1 筆交易完成後。
     * @return 是否已切換。
     */
    fun automationOpenReplay(step: Int): Boolean

    /**
     * 回放跳到指定步數。
     *
     * @param step 步數。
     * @return 是否已接受。
     */
    fun automationReplayTo(step: Int): Boolean

    /**
     * 從回放開啟顯示設定。
     *
     * @return 是否已切換。
     */
    fun automationOpenReplayVisibility(): Boolean

    /**
     * 顯示或恢復回放的遊戲面板：顯示時畫出本局資訊與每一家的玩家資訊，不顯示時恢復為預設值。
     *
     * @param shown 是否顯示。
     * @return 正在回放而已切換時為 true。
     */
    fun automationShowReplayPanels(shown: Boolean): Boolean

    /** 結束牌譜瀏覽並關閉畫面。 */
    fun automationClose()
}
