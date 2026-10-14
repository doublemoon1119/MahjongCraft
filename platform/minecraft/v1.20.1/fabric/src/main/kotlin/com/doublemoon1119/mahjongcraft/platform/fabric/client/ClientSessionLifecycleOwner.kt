package com.doublemoon1119.mahjongcraft.platform.fabric.client

/**
 * 由單一入口清除連線暫態資料；新連線不必依賴上一個 DISCONNECT 事件已送達。
 *
 * 清理動作只涵蓋連線資料，不包含持久化的使用者設定。JOIN 與 DISCONNECT
 * 使用同一份清單，每次呼叫各清理一次。
 * 本物件由客戶端組裝入口集中既有實例的清理動作，不另建立或管理這些服務的 DI 生命週期。
 *
 * @property clearHistoryQueries 清除歷史查詢配對與共用查詢設定。
 * @property clearHistoryArchiveStatus 清除保存狀態查詢配對。
 * @property clearProfiles 清除玩家名稱與外觀的連線快取。
 * @property clearHistoryChatEntries 清除結算聊天的歷史跳轉入口。
 * @property clearGameState 清除目前遊戲的客戶端快照。
 * @property clearAutomaticControl 清除自動操作的待確認要求，不刪除持久偏好。
 * @property clearDecisionTimer 清除決策計時狀態。
 * @property clearDecisionPrompt 清除待處理的決策提示。
 * @property clearDecisionHud 清除決策 HUD 的暫態呈現。
 * @property clearTileAppearance 清除桌級牌組外觀快取。
 */
internal class ClientSessionLifecycleOwner(
    private val clearHistoryQueries: () -> Unit,
    private val clearHistoryArchiveStatus: () -> Unit,
    private val clearProfiles: () -> Unit,
    private val clearHistoryChatEntries: () -> Unit,
    private val clearGameState: () -> Unit,
    private val clearAutomaticControl: () -> Unit,
    private val clearDecisionTimer: () -> Unit,
    private val clearDecisionPrompt: () -> Unit,
    private val clearDecisionHud: () -> Unit,
    private val clearTileAppearance: () -> Unit,
) {
    /** 開始新連線時清除上一個連線的全部暫態資料。 */
    fun beginSession() = clearTransientState()

    /** 結束連線時清除同一份暫態資料，不影響持久使用者偏好。 */
    fun endSession() = clearTransientState()

    /** 固定清理順序；共用設定由歷史查詢清理一次，其他協調器不重複重設。 */
    private fun clearTransientState() {
        clearHistoryQueries()
        clearHistoryArchiveStatus()
        clearProfiles()
        clearHistoryChatEntries()
        clearGameState()
        clearAutomaticControl()
        clearDecisionTimer()
        clearDecisionPrompt()
        clearDecisionHud()
        clearTileAppearance()
    }
}
